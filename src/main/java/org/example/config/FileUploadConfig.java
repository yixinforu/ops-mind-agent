package org.example.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
@ConfigurationProperties(prefix = "file.upload")
public class FileUploadConfig {

    private String path;
    private String allowedExtensions;

    public void setPath(String path) {
        this.path = path;
    }

    public void setAllowedExtensions(String allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }
}
