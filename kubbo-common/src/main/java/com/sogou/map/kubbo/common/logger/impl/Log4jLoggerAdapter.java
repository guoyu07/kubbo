package com.sogou.map.kubbo.common.logger.impl;

import org.apache.log4j.LogManager;

import com.sogou.map.kubbo.common.logger.Logger;
import com.sogou.map.kubbo.common.logger.LoggerAdapter;

public class Log4jLoggerAdapter implements LoggerAdapter {

    @Override
    public Logger getLogger(Class<?> key) {
        return new Log4jLogger(LogManager.getLogger(key));
    }

    @Override
    public Logger getLogger(String key) {
        return new Log4jLogger(LogManager.getLogger(key));
    }

}