package com.circleguard.promotion.service;

import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.repository.jpa.BuildingRepository;
import com.circleguard.promotion.repository.jpa.FloorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BuildingServiceTest {

    private BuildingRepository buildingRepository;
    private FloorRepository floorRepository;
    private BuildingService service;

    @BeforeEach
    void setUp() {
        buildingRepository = mock(BuildingRepository.class);
        floorRepository = mock(FloorRepository.class);
        service = new BuildingService(buildingRepository, floorRepository);
    }

    @Test
    void createBuilding_savesAndReturns() {
        Building saved = Building.builder()
                .id(UUID.randomUUID())
                .name("Library")
                .code("LIB")
                .description("Main library")
                .latitude(4.6)
                .longitude(-74.1)
                .address("Campus Rd 1")
                .build();

        when(buildingRepository.save(any())).thenReturn(saved);

        Building result = service.createBuilding("Library", "LIB", "Main library", 4.6, -74.1, "Campus Rd 1");

        assertNotNull(result);
        assertEquals("Library", result.getName());
        assertEquals("LIB", result.getCode());
        verify(buildingRepository).save(any(Building.class));
    }

    @Test
    void getAllBuildings_returnsList() {
        List<Building> buildings = List.of(
                Building.builder().id(UUID.randomUUID()).name("Building A").code("BA").build(),
                Building.builder().id(UUID.randomUUID()).name("Building B").code("BB").build()
        );
        when(buildingRepository.findAll()).thenReturn(buildings);

        List<Building> result = service.getAllBuildings();

        assertEquals(2, result.size());
    }

    @Test
    void updateBuilding_found_updatesAndSaves() {
        UUID id = UUID.randomUUID();
        Building existing = Building.builder()
                .id(id)
                .name("Old Name")
                .code("OLD")
                .description("Old desc")
                .latitude(1.0)
                .longitude(2.0)
                .address("Old Address")
                .build();

        when(buildingRepository.findById(id)).thenReturn(Optional.of(existing));
        when(buildingRepository.save(existing)).thenReturn(existing);

        Building result = service.updateBuilding(id, "New Name", "NEW", "New desc", 3.0, 4.0, "New Address");

        assertEquals("New Name", result.getName());
        assertEquals("NEW", result.getCode());
        assertEquals("New Address", result.getAddress());
        verify(buildingRepository).save(existing);
    }

    @Test
    void updateBuilding_notFound_throwsException() {
        UUID id = UUID.randomUUID();
        when(buildingRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> service.updateBuilding(id, "X", "X", "X", 0.0, 0.0, "X"));
    }

    @Test
    void deleteBuilding_noFloors_deletes() {
        UUID id = UUID.randomUUID();
        when(floorRepository.findByBuildingId(id)).thenReturn(List.of());

        assertDoesNotThrow(() -> service.deleteBuilding(id));

        verify(buildingRepository).deleteById(id);
    }

    @Test
    void deleteBuilding_withFloors_throwsException() {
        UUID id = UUID.randomUUID();
        when(floorRepository.findByBuildingId(id)).thenReturn(
                List.of(com.circleguard.promotion.model.Floor.builder()
                        .id(UUID.randomUUID()).floorNumber(1).build())
        );

        assertThrows(RuntimeException.class, () -> service.deleteBuilding(id));

        verify(buildingRepository, never()).deleteById(any());
    }
}
