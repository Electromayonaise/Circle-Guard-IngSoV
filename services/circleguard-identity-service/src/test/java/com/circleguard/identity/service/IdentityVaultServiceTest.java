package com.circleguard.identity.service;

import com.circleguard.identity.model.IdentityMapping;
import com.circleguard.identity.repository.IdentityMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityVaultServiceTest {

    private IdentityMappingRepository repository;
    private IdentityVaultService service;

    @BeforeEach
    void setUp() {
        repository = mock(IdentityMappingRepository.class);
        service = new IdentityVaultService(repository);
        ReflectionTestUtils.setField(service, "hashSalt", "test-salt");
    }

    @Test
    void getOrCreateAnonymousId_existingIdentity_returnsExistingAnonymousId() {
        UUID existingId = UUID.randomUUID();
        IdentityMapping existing = IdentityMapping.builder()
                .anonymousId(existingId)
                .realIdentity("user@example.com")
                .identityHash("someHash")
                .salt("salt")
                .build();

        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.of(existing));

        UUID result = service.getOrCreateAnonymousId("user@example.com");

        assertEquals(existingId, result);
        verify(repository, never()).save(any());
    }

    @Test
    void getOrCreateAnonymousId_newIdentity_savesAndReturnsNewId() {
        UUID newId = UUID.randomUUID();
        IdentityMapping saved = IdentityMapping.builder()
                .anonymousId(newId)
                .realIdentity("new@example.com")
                .identityHash("hash")
                .salt("salt")
                .build();

        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(saved);

        UUID result = service.getOrCreateAnonymousId("new@example.com");

        assertEquals(newId, result);
        verify(repository).save(any(IdentityMapping.class));
    }

    @Test
    void getOrCreateAnonymousId_sameIdentity_producesSameHash() {
        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> {
            IdentityMapping m = inv.getArgument(0);
            m = IdentityMapping.builder()
                    .anonymousId(UUID.randomUUID())
                    .realIdentity(m.getRealIdentity())
                    .identityHash(m.getIdentityHash())
                    .salt(m.getSalt())
                    .build();
            return m;
        });

        service.getOrCreateAnonymousId("user@example.com");
        service.getOrCreateAnonymousId("user@example.com");

        verify(repository, times(2)).findByIdentityHash(eq(captureHash("user@example.com")));
    }

    @Test
    void resolveRealIdentity_found_returnsIdentity() {
        UUID anonymousId = UUID.randomUUID();
        IdentityMapping mapping = IdentityMapping.builder()
                .anonymousId(anonymousId)
                .realIdentity("user@example.com")
                .identityHash("hash")
                .salt("salt")
                .build();

        when(repository.findById(anonymousId)).thenReturn(Optional.of(mapping));

        String result = service.resolveRealIdentity(anonymousId);

        assertEquals("user@example.com", result);
    }

    @Test
    void resolveRealIdentity_notFound_throws404() {
        UUID anonymousId = UUID.randomUUID();
        when(repository.findById(anonymousId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> service.resolveRealIdentity(anonymousId));
    }

    @Test
    void getOrCreateAnonymousId_differentIdentities_produceDifferentHashes() {
        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> {
            IdentityMapping m = inv.getArgument(0);
            return IdentityMapping.builder()
                    .anonymousId(UUID.randomUUID())
                    .realIdentity(m.getRealIdentity())
                    .identityHash(m.getIdentityHash())
                    .salt(m.getSalt())
                    .build();
        });

        service.getOrCreateAnonymousId("user1@example.com");
        service.getOrCreateAnonymousId("user2@example.com");

        verify(repository, times(2)).findByIdentityHash(argThat(hash -> hash != null));
    }

    private String captureHash(String identity) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((identity + "test-salt").getBytes());
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
