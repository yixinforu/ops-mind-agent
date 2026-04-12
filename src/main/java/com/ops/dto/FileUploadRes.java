package com.ops.dto;

import lombok.Data;

@Data
public class FileUploadRes {

    private String fileName;
    private String filePath;
    private Long fileSize;

    public FileUploadRes() {
    }

    public FileUploadRes(String fileName, String filePath, Long fileSize) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
    }

}
