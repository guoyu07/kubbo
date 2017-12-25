package com.sogou.map.kubbo.remote.session.codec;

import java.io.IOException;
import java.io.InputStream;

import com.sogou.map.kubbo.common.Version;
import com.sogou.map.kubbo.common.io.Bytes;
import com.sogou.map.kubbo.common.io.StreamUtils;
import com.sogou.map.kubbo.common.logger.Logger;
import com.sogou.map.kubbo.common.logger.LoggerFactory;
import com.sogou.map.kubbo.common.util.StringUtils;
import com.sogou.map.kubbo.remote.Channel;
import com.sogou.map.kubbo.remote.buffer.ChannelBuffer;
import com.sogou.map.kubbo.remote.buffer.ChannelBufferInputStream;
import com.sogou.map.kubbo.remote.buffer.ChannelBufferOutputStream;
import com.sogou.map.kubbo.remote.serialization.ObjectInput;
import com.sogou.map.kubbo.remote.serialization.ObjectOutput;
import com.sogou.map.kubbo.remote.serialization.Serialization;
import com.sogou.map.kubbo.remote.serialization.Serializations;
import com.sogou.map.kubbo.remote.session.EncodedMessage;
import com.sogou.map.kubbo.remote.session.Request;
import com.sogou.map.kubbo.remote.session.Response;
import com.sogou.map.kubbo.remote.transport.codec.TransportCodec;

/**
 * SessionCodec.
 * 
 * @author liufuliang
 */
public class SessionCodec extends TransportCodec {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionCodec.class);

    public static final String NAME = "session";

    // header length.
    protected static final int HEADER_LENGTH = 16;

    // magic header.
    protected static final short MAGIC = (short) 0xdabb;
    
    protected static final byte MAGIC_HIGH = Bytes.short2bytes(MAGIC)[0];
    
    protected static final byte MAGIC_LOW = Bytes.short2bytes(MAGIC)[1];

    // message flag.
    protected static final byte FLAG_REQUEST = (byte) 0x80;

    protected static final byte FLAG_TWOWAY = (byte) 0x40;

    protected static final byte FLAG_EVENT = (byte) 0x20;

    protected static final int SERIALIZATION_MASK = 0x1f;

    @Override
    public void encode(Channel channel, ChannelBuffer buffer, Object msg) throws IOException {
        if (msg instanceof Request) {
            encodeRequest(channel, buffer, (Request) msg);
        } else if (msg instanceof Response) {
            encodeResponse(channel, buffer, (Response) msg);
        } else {
            super.encode(channel, buffer, msg);
        }
    }

    @Override
    public Object decode(Channel channel, ChannelBuffer buffer) throws IOException {
        int readable = buffer.readableBytes();
        byte[] header = new byte[Math.min(readable, HEADER_LENGTH)];
        buffer.readBytes(header);
        return decode(channel, buffer, readable, header);
    }
    
    protected void encodeRequest(Channel channel, ChannelBuffer buffer, Request req) throws IOException {
        Serialization serialization = Serializations.getSerialization(channel.getUrl());
        // header.
        byte[] header = new byte[HEADER_LENGTH];
        // set magic number.
        header[0] = MAGIC_HIGH;
        header[1] = MAGIC_LOW;

        // set request and serialization flag.
        header[2] = FLAG_REQUEST;
        if (req.isTwoWay()) header[2] |= FLAG_TWOWAY;
        if (req.isEvent()) header[2] |= FLAG_EVENT;
        header[2] = (byte) (header[2] | serialization.getContentTypeId());

        // set request id.
        Bytes.long2bytes(req.getId(), header, 4);

        // encode request data.
        int savedWriteIndex = buffer.writerIndex();
        buffer.writerIndex(savedWriteIndex + HEADER_LENGTH);
        ChannelBufferOutputStream stream = new ChannelBufferOutputStream(buffer);

        ObjectOutput objectOutput = serialization.serialize(stream);
        try{
            if(req.isEvent()){
                encodeData(channel, objectOutput, req.getData());
            } else{
                encodeRequestData(channel, objectOutput, req.getData());
            }
            objectOutput.flushBuffer();
        } finally{
            Serializations.releaseSafely(objectOutput);
        }

        // flush stream
        stream.flush();
        
        // len
        int len = stream.writtenBytes();
        checkPayload(channel, len);
        Bytes.int2bytes(len, header, 12);

        // write
        buffer.writerIndex(savedWriteIndex);
        buffer.writeBytes(header); // write header.
        buffer.writerIndex(savedWriteIndex + HEADER_LENGTH + len);
        
        // close stream
        stream.close();
    }
    
    protected void encodeResponse(Channel channel, ChannelBuffer buffer, Response res) throws IOException {
        try {
            Serialization serialization = Serializations.getSerialization(channel.getUrl());
            // header.
            byte[] header = new byte[HEADER_LENGTH];
            // set magic number.
            header[0] = MAGIC_HIGH;
            header[1] = MAGIC_LOW;
            
            // set request and serialization flag.
            header[2] = serialization.getContentTypeId();
            if (res.isEvent()) header[2] |= FLAG_EVENT;
            
            // set response status.
            byte status = res.getStatus();
            header[3] = status;
            
            // set request id.
            Bytes.long2bytes(res.getId(), header, 4);

            int savedWriteIndex = buffer.writerIndex();
            buffer.writerIndex(savedWriteIndex + HEADER_LENGTH);
            ChannelBufferOutputStream stream = new ChannelBufferOutputStream(buffer);
            ObjectOutput objectOutput = serialization.serialize(stream);
            try {
                // encode response data or error message.
                if (res.isOK()) {
                    if (res.isEvent()) {
                        encodeData(channel, serialization, stream, res.getResult());
                    } else if (res.getResult() instanceof EncodedMessage){
                        EncodedMessage ecodedResult = (EncodedMessage)res.getResult();
                        stream.write(ecodedResult.getBytes());
                    } else {
                        encodeResponseData(channel, objectOutput, res.getResult());
                    }
                } else {
                    objectOutput.writeUTF(res.getErrorMessage());
                }
                objectOutput.flushBuffer();
            } finally {
                Serializations.releaseSafely(objectOutput);
            }
           
            // flush stream
            stream.flush();
            
            // len
            int len = stream.writtenBytes();
            checkPayload(channel, len);
            Bytes.int2bytes(len, header, 12);
            
            // write
            buffer.writerIndex(savedWriteIndex);
            buffer.writeBytes(header); // write header.
            buffer.writerIndex(savedWriteIndex + HEADER_LENGTH + len);
            
            // close stream
            stream.close();
        } catch (Throwable t) {
            if (!res.isEvent() && res.isBadResponse()) {
                logger.warn("Fail to encode response: " + res + ", send exception info instead, cause: " + t.getMessage(), t);
                buffer.clear();
                Response r = new Response(res.getId(), res.getVersion());
                r.setStatus(Response.BAD_RESPONSE);
                r.setErrorMessage("Failed to send response: " + res + ", cause: " + StringUtils.toString(t));
                encodeResponse(channel, buffer, r);
                return;
            }
            
            // 重新抛出收到的异常
            if (t instanceof IOException) {
                throw (IOException) t;
            } else if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else  {
                throw new RuntimeException(t.getMessage(), t);
            }
        }
    }
    
    protected Object decode(Channel channel, ChannelBuffer buffer, int readable, byte[] header) throws IOException {
        // check magic number.
        if (readable > 0 && header[0] != MAGIC_HIGH 
                || readable > 1 && header[1] != MAGIC_LOW) {
            int length = header.length;
            if (header.length < readable) {
                header = Bytes.copyOf(header, readable);
                buffer.readBytes(header, length, readable - length);
            }
            byte[] msg = header;
            for (int i = 1; i < msg.length - 1; i ++) {
                if (header[i] == MAGIC_HIGH && header[i + 1] == MAGIC_LOW) {
                    buffer.readerIndex(buffer.readerIndex() - msg.length + i);
                    msg = Bytes.copyOf(msg, i);
                    break;
                }
            }
            return msg;
        }
        // check length.
        if (readable < HEADER_LENGTH) {
            return DecodeResult.NEED_MORE_INPUT;
        }

        // get data length.
        int len = Bytes.bytes2int(header, 12);
        checkPayload(channel, len);

        int tt = len + HEADER_LENGTH;
        if( readable < tt ) {
            return DecodeResult.NEED_MORE_INPUT;
        }

        // wrap input stream.
        ChannelBufferInputStream input = new ChannelBufferInputStream(buffer, len);
                
        try {
            return decodeBody(channel, input, header);
        } finally {
            if (input.available() > 0) {
                try {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Skip input stream " + input.available());
                    }
                    StreamUtils.skipUnusedStream(input);
                } catch (IOException e) {
                    logger.warn(e.getMessage(), e);
                }
            }
        }
    }
    
    protected Object decodeBody(Channel channel, InputStream is, byte[] header) throws IOException {
        byte flag = header[2];
        byte serializationId = (byte) (flag & SERIALIZATION_MASK);
        Serialization serialization = Serializations.getSerialization(channel.getUrl(), serializationId);

        // get request id.
        long id = Bytes.bytes2long(header, 4);
        
        if ((flag & FLAG_REQUEST) == 0) {  // decode response.
            Response res = new Response(id);
            if ((flag & FLAG_EVENT) != 0) {
                res.setEvent(true);
            }
            // get status.
            byte status = header[3];
            res.setStatus(status);
            if (status == Response.OK) {
                try {
                    Object data;
                    if (res.isEvent()) {
                        data = decodeData(channel, serialization, is);
                    } else {
                        data = decodeResponseData(channel, serialization, is, res);
                    }
                    res.setResult(data);
                } catch (Throwable t) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Decode response failed: " + t.getMessage(), t);
                    }
                    res.setStatus(Response.CLIENT_ERROR);
                    res.setErrorMessage(StringUtils.toString(t));
                }
            } else {
                ObjectInput in = serialization.deserialize(is);
                res.setErrorMessage(in.readUTF());
                Serializations.releaseSafely(in);
            }
            return res;
        } else {  // decode request.
            Request req = new Request(id);
            req.setVersion(Version.getVersion());
            req.setTwoWay((flag & FLAG_TWOWAY) != 0);
            if ((flag & FLAG_EVENT) != 0) {
                req.setEvent(true);
            }
            try {
                Object data;
                if (req.isEvent()) {
                    data = decodeData(channel, serialization, is);
                } else {
                    data = decodeRequestData(channel, serialization, is, req);
                }
                req.setData(data);
            } catch (Throwable t) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Decode request failed: " + t.getMessage(), t);
                }
                // bad request
                req.setBroken(true);
                req.setData(t);
            }
            return req;
        }
    }

    
//    protected Object decodeRequestData(Channel channel, ObjectInput in, Request request) throws IOException {
//        return decodeData(channel, in);
//    }
//
//    protected Object decodeResponseData(Channel channel, ObjectInput in, Response response) throws IOException {
//        return decodeData(channel, in);
//    }
    
    protected void encodeRequestData(Channel channel, ObjectOutput out, Object data) throws IOException {
        encodeData(out, data);
    }

    protected void encodeResponseData(Channel channel, ObjectOutput out, Object data) throws IOException {
        encodeData(out, data);
    }
    
    protected Object decodeRequestData(Channel channel,  Serialization serialization, InputStream input,  Request request) throws IOException {
        return decodeData(channel, serialization, input);
    }

    protected Object decodeResponseData(Channel channel, Serialization serialization, InputStream input, Response response) throws IOException {
        return decodeData(channel, serialization, input);
    }
}