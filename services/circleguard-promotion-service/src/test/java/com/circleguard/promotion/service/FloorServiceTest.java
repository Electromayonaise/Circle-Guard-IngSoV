package com.circleguard.promotion.service;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.repository.jpa.AccessPointRepository;
import com.circleguard.promotion.repository.jpa.BuildingRepository;
import com.circleguard.promotion.repository.jpa.FloorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FloorServiceTest {

    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private FloorRepository floorRepository;
    @Mock
    private AccessPointRepository accessPointRepository;

    private FloorService floorService;

    @BeforeEach
    void setUp() {
        floorService = new FloorService(buildingRepository, floorRepository, accessPointRepository);
    }

    @Test
    void addFloor_buildingExists_savesAndReturns() {
        UUID buildingId = UUID.randomUUID();
        Building building = Building.builder().id(buildingId).name("Library").code("LIB").build();
        Floor saved = Floor.builder().id(UUID.randomUUID()).building(building).floorNumber(1).name("Ground").build();

        when(buildingRepository.findById(buildingId)).thenReturn(Optional.of(building));
        when(floorRepository.save(any())).thenReturn(saved);

        Floor result = floorService.addFloor(buildingId, 1, "Ground");

        assertNotNull(result);
        assertEquals(1, result.getFloorNumber());
        verify(floorRepository).save(any(Floor.class));
    }

    @Test
    void addFloor_buildingNotFound_throwsException() {
        UUID buildingId = UUID.randomUUID();
        when(buildingRepository.findById(buildingId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> floorService.addFloor(buildingId, 1, "Ground"));
    }

    @Test
    void getFloorsByBuilding_returnsList() {
        UUID buildingId = UUID.randomUUID();
        List<Floor> floors = List.of(
                Floor.builder().id(UUID.randomUUID()).floorNumber(1).build(),
                Floor.builder().id(UUID.randomUUID()).floorNumber(2).build()
        );
        when(floorRepository.findByBuildingId(buildingId)).thenReturn(floors);

        List<Floor> result = floorService.getFloorsByBuilding(buildingId);

        assertEquals(2, result.size());
    }

    @Test
    void updateFloor_allFieldsPresent_updatesAll() {
        UUID floorId = UUID.randomUUID();
        Floor floor = Floor.builder()
                .id(floorId)
                .name("Level 1")
                .floorNumber(1)
                .floorPlanUrl("http://old.url")
                .build();

        when(floorRepository.findById(floorId)).thenReturn(Optional.of(floor));
        when(floorRepository.save(any(Floor.class))).thenAnswer(i -> i.getArguments()[0]);

        Floor updated = floorService.updateFloor(floorId, 2, "Level 2", "http://new.url");

        assertEquals(2, updated.getFloorNumber());
        assertEquals("Level 2", updated.getName());
        assertEquals("http://new.url", updated.getFloorPlanUrl());
        verify(floorRepository).save(floor);
    }

    @Test
    void updateFloor_nullFields_skipsUpdate() {
        UUID floorId = UUID.randomUUID();
        Floor floor = Floor.builder()
                .id(floorId)
                .floorPlanUrl("http://old.url")
                .name("Keep Me")
                .floorNumber(3)
                .build();

        when(floorRepository.findById(floorId)).thenReturn(Optional.of(floor));
        when(floorRepository.save(any(Floor.class))).thenAnswer(i -> i.getArguments()[0]);

        Floor updated = floorService.updateFloor(floorId, null, null, null);

        assertEquals("http://old.url", updated.getFloorPlanUrl());
        assertEquals("Keep Me", updated.getName());
        verify(floorRepository).save(floor);
    }

    @Test
    void updateFloor_notFound_throwsException() {
        UUID id = UUID.randomUUID();
        when(floorRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> floorService.updateFloor(id, 1, "Name", null));
    }

    @Test
    void deleteFloor_noAccessPoints_deletes() {
        UUID id = UUID.randomUUID();
        when(accessPointRepository.findByFloorId(id)).thenReturn(List.of());

        assertDoesNotThrow(() -> floorService.deleteFloor(id));

        verify(floorRepository).deleteById(id);
    }

    @Test
    void deleteFloor_withAccessPoints_throwsException() {
        UUID id = UUID.randomUUID();
        when(accessPointRepository.findByFloorId(id)).thenReturn(
                List.of(AccessPoint.builder().id(UUID.randomUUID()).macAddress("AA:BB:CC:DD:EE:FF").build())
        );

        assertThrows(RuntimeException.class, () -> floorService.deleteFloor(id));

        verify(floorRepository, never()).deleteById(any());
    }
}
