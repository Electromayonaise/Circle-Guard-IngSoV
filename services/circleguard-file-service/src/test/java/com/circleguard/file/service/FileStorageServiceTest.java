package com.circleguard.file.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new FileStorageService();
    }

    @Test
    void saveFile_returnsFilenameWithOriginalName() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "certificate.pdf", "application/pdf", "test content".getBytes());

        String filename = service.saveFile(file);

        assertNotNull(filename);
        assertTrue(filename.endsWith("_certificate.pdf"));
    }

    @Test
    void saveFile_generatesUniqueFilenames() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "content".getBytes());

        String name1 = service.saveFile(file);
        String name2 = service.saveFile(file);

        assertNotEquals(name1, name2);
    }

    @Test
    void loadFile_returnsNull() {
        Object result = service.loadFile("any-file.pdf");

        assertNull(result);
    }
}
