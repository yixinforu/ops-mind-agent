package com.ops.controller;

import com.ops.config.FileUploadConfig;
import com.ops.dto.FileUploadRes;
import com.ops.dto.common.ApiResponse;
import com.ops.service.VectorIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件上传控制器
 * 负责接收知识文档上传请求，并在上传成功后触发向量索引构建。
 */
@RestController
public class FileUploadController {

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    @Autowired
    private FileUploadConfig fileUploadConfig;

    @Autowired
    private VectorIndexService vectorIndexService;

    /**
     * 上传知识文档。
     */
    @PostMapping(value = "/api/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<?>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("文件不能为空"));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("文件名不能为空"));
        }

        String fileExtension = getFileExtension(originalFilename);
        if (!isAllowedExtension(fileExtension)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("不支持的文件格式，仅支持: " + fileUploadConfig.getAllowedExtensions()));
        }

        try {
            String uploadPath = fileUploadConfig.getPath();
            Path uploadDir = Paths.get(uploadPath).normalize();
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // 使用原始文件名实现基于文件名的覆盖更新。
            Path filePath = uploadDir.resolve(originalFilename).normalize();
            if (Files.exists(filePath)) {
                logger.info("文件已存在，将覆盖: {}", filePath);
                Files.delete(filePath);
            }

            Files.copy(file.getInputStream(), filePath);
            logger.info("文件上传成功: {}", filePath);

            try {
                logger.info("开始为上传文件创建向量索引: {}", filePath);
                vectorIndexService.indexSingleFile(filePath.toString());
                logger.info("向量索引创建成功: {}", filePath);
            } catch (Exception e) {
                logger.error("向量索引创建失败: {}, 错误: {}", filePath, e.getMessage(), e);
            }

            FileUploadRes response = new FileUploadRes(
                    originalFilename,
                    filePath.toString(),
                    file.getSize()
            );
            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("文件上传失败: " + e.getMessage()));
        }
    }

    private String getFileExtension(String filename) {
        int lastIndexOf = filename.lastIndexOf('.');
        if (lastIndexOf == -1) {
            return "";
        }
        return filename.substring(lastIndexOf + 1).toLowerCase();
    }

    private boolean isAllowedExtension(String extension) {
        String allowedExtensions = fileUploadConfig.getAllowedExtensions();
        if (allowedExtensions == null || allowedExtensions.isEmpty()) {
            return false;
        }
        List<String> allowedList = Arrays.stream(allowedExtensions.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());
        return allowedList.contains(extension.toLowerCase());
    }
}
