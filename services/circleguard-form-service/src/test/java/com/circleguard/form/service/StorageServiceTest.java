package com.circleguard.form.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StorageServiceTest {

    @TempDir
    Path tempDir;

    private StorageService service() {
        return new StorageService(tempDir.toString());
    }

    @Test
    void store_validFilename_returnsUuidPrefixedName() {
        StorageService svc = service();
        MockMultipartFile file = new MockMultipartFile(
            "file", "report.pdf", "application/pdf", "content".getBytes()
        );

        String stored = svc.store(file);

        assertTrue(stored.endsWith("_report.pdf"));
        assertTrue(tempDir.resolve(stored).toFile().exists());
    }

    @Test
    void store_pathTraversalWithDotDot_throwsException() {
        StorageService svc = service();
        MockMultipartFile file = new MockMultipartFile(
            "file", "../../etc/passwd", "text/plain", "evil".getBytes()
        );

        RuntimeException ex = assertThrows(RuntimeException.class, () -> svc.store(file));
        assertTrue(ex.getMessage().contains("Invalid filename"));
    }

    @Test
    void store_filenameWithSlash_throwsException() {
        StorageService svc = service();
        MockMultipartFile file = new MockMultipartFile(
            "file", "subdir/evil.txt", "text/plain", "evil".getBytes()
        );

        RuntimeException ex = assertThrows(RuntimeException.class, () -> svc.store(file));
        assertTrue(ex.getMessage().contains("Invalid filename"));
    }

    @Test
    void store_nullFilename_throwsException() {
        StorageService svc = service();
        MockMultipartFile file = new MockMultipartFile(
            "file", null, "text/plain", "data".getBytes()
        );

        assertThrows(RuntimeException.class, () -> svc.store(file));
    }
}
