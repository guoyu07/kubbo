/**
 * 
 */
package com.sogou.map.kubbo.boot.configuration.element;

import com.sogou.map.kubbo.boot.configuration.Configuration;
import com.sogou.map.kubbo.common.Constants;

/**
 * @author liufuliang
 *
 */
public class ApplicationConfiguration implements Configuration{
    private static final long serialVersionUID = 1L;
    public static final String TAG = "application";
    
    /** 应用名称 */
    String name = Constants.DEFAULT_APPLICATION_NAME;

    /** 应用根目录 */
    String home;
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHome() {
        return home;
    }

    public void setHome(String home) {
        this.home = home;
    }
    
}
