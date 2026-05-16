package com.circleguard.promotion.service;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.repository.jpa.AccessPointRepository;
import com.circleguard.promotion.repository.jpa.BuildingRepository;
import com.circleguard.promotion.repository.jpa.FloorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpatialServiceTest {

    private BuildingRepository buildingRepository;
    private FloorRepository floorRepository;
    private AccessPointRepository accessPointRepository;
    private SpatialService service;

    @BeforeEach
    void setUp() {
        buildingRepository = mock(BuildingRepository.class);
        floorRepository = mock(FloorRepository.class);
        accessPointRepository = mock(AccessPointRepository.class);
        service = new SpatialService(buildingRepository, floorRepository, accessPointRepository);
    }

    @Test
    void createBuilding_savesAndReturns() {
        Building saved = Building.builder().id(UUID.randomUUID()).name("Hall").code("H1").build();
        when(buildingRepository.save(any())).thenReturn(saved);

        Building result = service.createBuilding("Hall", "H1", "Main Hall");

        assertNotNull(result);
        assertEquals("Hall", result.getName());
        verify(buildingRepository).save(any(Building.class));
    }

    @Test
    void getAllBuildings_returnsList() {
        List<Building> buildings = List.of(
                Building.builder().id(UUID.randomUUID()).name("A").code("A").build(),
                Building.builder().id(UUID.randomUUID()).name("B").code("B").build()
        );
        when(buildingRepository.findAll()).thenReturn(buildings);

        List<Building> result = service.getAllBuildings();

        assertEquals(2, result.size());
    }

    @Test
    void updateBuilding_found_updatesAndSaves() {
        UUID id = UUID.randomUUID();
        Building existing = Building.builder().id(id).name("Old").code("OLD").description("Old desc").build();
        when(buildingRepository.findById(id)).thenReturn(Optional.of(existing));
        when(buildingRepository.save(existing)).thenReturn(existing);

        Building result = service.updateBuilding(id, "New", "NEW", "New desc");

        assertEquals("New", result.getName());
        assertEquals("NEW", result.getCode());
        verify(buildingRepository).save(existing);
    }

    @Test
    void updateBuilding_notFound_throwsException() {
        UUID id = UUID.randomUUID();
        when(buildingRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.updateBuilding(id, "X", "X", "X"));
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
                List.of(Floor.builder().id(UUID.randomUUID()).floorNumber(1).build())
        );

        assertThrows(RuntimeException.class, () -> service.deleteBuilding(id));

        verify(buildingRepository, never()).deleteById(any());
    }

    @Test
    void addFloor_buildingExists_savesAndReturns() {
        UUID buildingId = UUID.randomUUID();
        Building building = Building.builder().id(buildingId).name("Hall").code("H1").build();
        Floor saved = Floor.builder().id(UUID.randomUUID()).floorNumber(1).name("Ground").build();

        when(buildingRepository.findById(buildingId)).thenReturn(Optional.of(building));
        when(floorRepository.save(any())).thenReturn(saved);

        Floor result = service.addFloor(buildingId, 1, "Ground");

        assertEquals(1, result.getFloorNumber());
        verify(floorRepository).save(any(Floor.class));
    }

    @Test
    void addFloor_buildingNotFound_throwsException() {
        UUID buildingId = UUID.randomUUID();
        when(buildingRepository.findById(buildingId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.addFloor(buildingId, 1, "Ground"));
    }

    @Test
    void getFloorsByBuilding_returnsList() {
        UUID buildingId = UUID.randomUUID();
        when(floorRepository.findByBuildingId(buildingId)).thenReturn(
                List.of(Floor.builder().id(UUID.randomUUID()).floorNumber(1).build())
        );

        List<Floor> result = service.getFloorsByBuilding(buildingId);

        assertEquals(1, result.size());
    }

    @Test
    void updateFloor_found_updatesFields() {
        UUID id = UUID.randomUUID();
        Floor existing = Floor.builder().id(id).floorNumber(1).name("Old").build();
        when(floorRepository.findById(id)).thenReturn(Optional.of(existing));
        when(floorRepository.save(existing)).thenReturn(existing);

        Floor result = service.updateFloor(id, 2, "New");

        assertEquals(2, result.getFloorNumber());
        assertEquals("New", result.getName());
        verify(floorRepository).save(existing);
    }

    @Test
    void updateFloor_notFound_throwsException() {
        UUID id = UUID.randomUUID();
        when(floorRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.updateFloor(id, 1, "Name"));
    }

    @Test
    void deleteFloor_noAccessPoints_deletes() {
        UUID id = UUID.randomUUID();
        when(accessPointRepository.findByFloorId(id)).thenReturn(List.of());

        assertDoesNotThrow(() -> service.deleteFloor(id));

        verify(floorRepository).deleteById(id);
    }

    @Test
    void deleteFloor_withAccessPoints_throwsException() {
        UUID id = UUID.randomUUID();
        when(accessPointRepository.findByFloorId(id)).thenReturn(
                List.of(AccessPoint.builder().id(UUID.randomUUID()).macAddress("AA:BB").build())
        );

        assertThrows(RuntimeException.class, () -> service.deleteFloor(id));

        verify(floorRepository, never()).deleteById(any());
    }

    @Test
    void registerAccessPoint_floorExists_savesAndReturns() {
        UUID floorId = UUID.randomUUID();
        Floor floor = Floor.builder().id(floorId).floorNumber(1).build();
        AccessPoint saved = AccessPoint.builder().id(UUID.randomUUID()).macAddress("AA:BB:CC").build();

        when(floorRepository.findById(floorId)).thenReturn(Optional.of(floor));
        when(accessPointRepository.save(any())).thenReturn(saved);

        AccessPoint result = service.registerAccessPoint(floorId, "AA:BB:CC", 1.0, 2.0, "AP1");

        assertNotNull(result);
        verify(accessPointRepository).save(any(AccessPoint.class));
    }

    @Test
    void registerAccessPoint_floorNotFound_throwsException() {
        UUID floorId = UUID.randomUUID();
        when(floorRepository.findById(floorId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> service.registerAccessPoint(floorId, "MAC", 0.0, 0.0, "AP"));
    }

    @Test
    void getAccessPoint_returnsOptional() {
        UUID id = UUID.randomUUID();
        AccessPoint ap = AccessPoint.builder().id(id).build();
        when(accessPointRepository.findById(id)).thenReturn(Optional.of(ap));

        assertTrue(service.getAccessPoint(id).isPresent());
    }

    @Test
    void getAccessPointsByFloor_returnsList() {
        UUID floorId = UUID.randomUUID();
        when(accessPointRepository.findByFloorId(floorId)).thenReturn(
                List.of(AccessPoint.builder().id(UUID.randomUUID()).build())
        );

        assertEquals(1, service.getAccessPointsByFloor(floorId).size());
    }

    @Test
    void updateAccessPoint_found_updatesAndSaves() {
        UUID id = UUID.randomUUID();
        AccessPoint existing = AccessPoint.builder().id(id).macAddress("OLD").coordinateX(0.0).coordinateY(0.0).name("Old").build();
        when(accessPointRepository.findById(id)).thenReturn(Optional.of(existing));
        when(accessPointRepository.save(existing)).thenReturn(existing);

        AccessPoint result = service.updateAccessPoint(id, "NEW", 5.0, 6.0, "New");

        assertEquals("NEW", result.getMacAddress());
        verify(accessPointRepository).save(existing);
    }

    @Test
    void updateAccessPoint_notFound_throwsException() {
        UUID id = UUID.randomUUID();
        when(accessPointRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> service.updateAccessPoint(id, "MAC", 0.0, 0.0, "AP"));
    }

    @Test
    void deleteAccessPoint_callsDeleteById() {
        UUID id = UUID.randomUUID();

        assertDoesNotThrow(() -> service.deleteAccessPoint(id));

        verify(accessPointRepository).deleteById(id);
    }
}
