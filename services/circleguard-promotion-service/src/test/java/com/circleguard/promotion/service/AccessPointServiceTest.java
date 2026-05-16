package com.circleguard.promotion.service;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.repository.jpa.AccessPointRepository;
import com.circleguard.promotion.repository.jpa.FloorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessPointServiceTest {

    private AccessPointRepository accessPointRepository;
    private FloorRepository floorRepository;
    private AccessPointService service;

    @BeforeEach
    void setUp() {
        accessPointRepository = mock(AccessPointRepository.class);
        floorRepository = mock(FloorRepository.class);
        service = new AccessPointService(accessPointRepository, floorRepository);
    }

    @Test
    void registerAccessPoint_floorExists_savesAndReturns() {
        UUID floorId = UUID.randomUUID();
        Floor floor = Floor.builder().id(floorId).floorNumber(1).build();
        AccessPoint saved = AccessPoint.builder()
                .id(UUID.randomUUID())
                .floor(floor)
                .macAddress("AA:BB:CC:DD:EE:FF")
                .coordinateX(10.0)
                .coordinateY(20.0)
                .name("AP-01")
                .build();

        when(floorRepository.findById(floorId)).thenReturn(Optional.of(floor));
        when(accessPointRepository.save(any())).thenReturn(saved);

        AccessPoint result = service.registerAccessPoint(floorId, "AA:BB:CC:DD:EE:FF", 10.0, 20.0, "AP-01");

        assertNotNull(result);
        assertEquals("AA:BB:CC:DD:EE:FF", result.getMacAddress());
        assertEquals("AP-01", result.getName());
        verify(accessPointRepository).save(any(AccessPoint.class));
    }

    @Test
    void registerAccessPoint_floorNotFound_throwsException() {
        UUID floorId = UUID.randomUUID();
        when(floorRepository.findById(floorId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> service.registerAccessPoint(floorId, "AA:BB:CC:DD:EE:FF", 1.0, 2.0, "AP"));
    }

    @Test
    void getAccessPoint_found_returnsOptional() {
        UUID id = UUID.randomUUID();
        AccessPoint ap = AccessPoint.builder().id(id).macAddress("AA:BB:CC:DD:EE:FF").build();
        when(accessPointRepository.findById(id)).thenReturn(Optional.of(ap));

        Optional<AccessPoint> result = service.getAccessPoint(id);

        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
    }

    @Test
    void getAccessPoint_notFound_returnsEmpty() {
        UUID id = UUID.randomUUID();
        when(accessPointRepository.findById(id)).thenReturn(Optional.empty());

        Optional<AccessPoint> result = service.getAccessPoint(id);

        assertFalse(result.isPresent());
    }

    @Test
    void getAccessPointsByFloor_returnsList() {
        UUID floorId = UUID.randomUUID();
        List<AccessPoint> aps = List.of(
                AccessPoint.builder().id(UUID.randomUUID()).macAddress("AA:BB:CC:DD:EE:01").build(),
                AccessPoint.builder().id(UUID.randomUUID()).macAddress("AA:BB:CC:DD:EE:02").build()
        );
        when(accessPointRepository.findByFloorId(floorId)).thenReturn(aps);

        List<AccessPoint> result = service.getAccessPointsByFloor(floorId);

        assertEquals(2, result.size());
    }

    @Test
    void updateAccessPoint_found_updatesAndSaves() {
        UUID id = UUID.randomUUID();
        AccessPoint existing = AccessPoint.builder()
                .id(id)
                .macAddress("OLD:MAC")
                .coordinateX(1.0)
                .coordinateY(2.0)
                .name("Old AP")
                .build();

        when(accessPointRepository.findById(id)).thenReturn(Optional.of(existing));
        when(accessPointRepository.save(existing)).thenReturn(existing);

        AccessPoint result = service.updateAccessPoint(id, "NEW:MAC", 5.0, 6.0, "New AP");

        assertEquals("NEW:MAC", result.getMacAddress());
        assertEquals("New AP", result.getName());
        assertEquals(5.0, result.getCoordinateX());
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
