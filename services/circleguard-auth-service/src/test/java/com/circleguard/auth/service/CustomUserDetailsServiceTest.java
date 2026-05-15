package com.circleguard.auth.service;

import com.circleguard.auth.model.LocalUser;
import com.circleguard.auth.model.Permission;
import com.circleguard.auth.model.Role;
import com.circleguard.auth.repository.LocalUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomUserDetailsServiceTest {

    private LocalUserRepository userRepository;
    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(LocalUserRepository.class);
        service = new CustomUserDetailsService(userRepository);
    }

    @Test
    void loadUserByUsername_activeUser_returnsUserDetails() {
        Permission permission = Permission.builder().id(UUID.randomUUID()).name("survey:submit").build();
        Role role = Role.builder().id(UUID.randomUUID()).name("STUDENT").permissions(Set.of(permission)).build();
        LocalUser user = LocalUser.builder()
                .id(UUID.randomUUID())
                .username("student1")
                .password("hashed")
                .isActive(true)
                .roles(Set.of(role))
                .build();

        when(userRepository.findByUsername("student1")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("student1");

        assertEquals("student1", details.getUsername());
        assertEquals("hashed", details.getPassword());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT")));
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("survey:submit")));
    }

    @Test
    void loadUserByUsername_userNotFound_throwsUsernameNotFoundException() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("unknown"));
    }

    @Test
    void loadUserByUsername_inactiveUser_throwsDisabledException() {
        LocalUser user = LocalUser.builder()
                .id(UUID.randomUUID())
                .username("disabled")
                .password("hashed")
                .isActive(false)
                .roles(Set.of())
                .build();

        when(userRepository.findByUsername("disabled")).thenReturn(Optional.of(user));

        assertThrows(DisabledException.class,
                () -> service.loadUserByUsername("disabled"));
    }

    @Test
    void loadUserByUsername_roleWithMultiplePermissions_allGranted() {
        Permission p1 = Permission.builder().id(UUID.randomUUID()).name("alert:receive_priority").build();
        Permission p2 = Permission.builder().id(UUID.randomUUID()).name("health:confirm_positive").build();
        Role role = Role.builder().id(UUID.randomUUID()).name("HEALTH_CENTER").permissions(Set.of(p1, p2)).build();
        LocalUser user = LocalUser.builder()
                .id(UUID.randomUUID())
                .username("healthcenter")
                .password("hashed")
                .isActive(true)
                .roles(Set.of(role))
                .build();

        when(userRepository.findByUsername("healthcenter")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("healthcenter");

        assertEquals(3, details.getAuthorities().size()); // ROLE_HEALTH_CENTER + 2 permissions
    }
}
