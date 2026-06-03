package com.circleguard.form.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class StorageService {

    private final Path root;

    public StorageService(@Value("${storage.upload-dir:/opt/circleguard/uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage", e);
        }
    }

    public String store(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank() || originalFilename.contains("..") || originalFilename.contains("/")) {
            throw new RuntimeException("Invalid filename: " + originalFilename);
        }
        try {
            String filename = UUID.randomUUID() + "_" + originalFilename;
            Path target = this.root.resolve(filename).normalize();
            if (!target.startsWith(this.root.normalize())) {
                throw new RuntimeException("Path traversal attempt detected");
            }
            Files.copy(file.getInputStream(), target);
            return filename;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Could not store the file. Error: " + e.getMessage());
        }
    }
}
