package com.parkit.parkingsystem;

import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ParkingServiceTest {

    private static ParkingService parkingService;

    @Mock
    private static InputReaderUtil inputReaderUtil;
    @Mock
    private static ParkingSpotDAO parkingSpotDAO;
    @Mock
    private static TicketDAO ticketDAO;

    @BeforeEach
    public void setUpPerTest() throws Exception {
        // Passe chaque stub en lenient() pour éviter les UnnecessaryStubbingException
        lenient().when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");

        ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, false);
        Ticket ticket = new Ticket();
        ticket.setInTime(new Date(System.currentTimeMillis() - (60 * 60 * 1000)));
        ticket.setParkingSpot(parkingSpot);
        ticket.setVehicleRegNumber("ABCDEF");

        lenient().when(ticketDAO.getTicket(anyString())).thenReturn(ticket);
        lenient().when(ticketDAO.updateTicket(any(Ticket.class))).thenReturn(true);

        lenient().when(parkingSpotDAO.updateParking(any(ParkingSpot.class))).thenReturn(true);

        parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
    }

    @Test
    public void processExitingVehicleTest() throws Exception {
        // 1) immatriculation
        when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABC123");

        // 2) ticket existant
        Ticket ticket = new Ticket();
        ticket.setVehicleRegNumber("ABC123");
        ticket.setInTime(new Date(System.currentTimeMillis() - 3600_000));
        ticket.setParkingSpot(new ParkingSpot(1, ParkingType.CAR, false));
        when(ticketDAO.getTicket("ABC123")).thenReturn(ticket);

        // 3) stub des retours des DAO (mise à jour + compteur)
        doReturn(true).when(ticketDAO).updateTicket(any(Ticket.class));
        doReturn(true).when(parkingSpotDAO).updateParking(any(ParkingSpot.class));
        when(ticketDAO.getNbTicket("ABC123")).thenReturn(1);

        // 4) appel
        parkingService.processExitingVehicle();

        // 5) vérifs
        verify(ticketDAO).getTicket("ABC123");
        verify(ticketDAO).getNbTicket("ABC123");
        verify(ticketDAO).updateTicket(any(Ticket.class));
        verify(parkingSpotDAO).updateParking(any(ParkingSpot.class));
    }
    
    @Test
    public void testProcessIncomingVehicle() throws Exception {
        // 1) choix du type et immatriculation
        when(inputReaderUtil.readSelection()).thenReturn(1);                  // 1 = CAR
        when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("XYZ123");

        // 2) parking spot disponible
        when(parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR)).thenReturn(5);

        // 3) on stub la mise à jour et l’enregistrement du ticket
        doReturn(true).when(parkingSpotDAO).updateParking(any(ParkingSpot.class));
        doReturn(true).when(ticketDAO).saveTicket(any(Ticket.class));

        // 4) appel de la méthode sous test
        parkingService.processIncomingVehicle();

        // 5) vérifications
        verify(parkingSpotDAO).getNextAvailableSlot(ParkingType.CAR);
        verify(parkingSpotDAO).updateParking(argThat(spot ->
            spot.getId() == 5 && spot.getParkingType() == ParkingType.CAR && !spot.isAvailable()
        ));
        verify(ticketDAO).saveTicket(argThat(ticket ->
            "XYZ123".equals(ticket.getVehicleRegNumber()) &&
            ticket.getInTime() != null &&
            ticket.getParkingSpot().getId() == 5
        ));
    }
    
    @Test
    public void processExitingVehicleTestUnableUpdate() throws Exception {
        // 1) on simule la saisie de l'immatriculation
        when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("FAIL123");

        // 2) on prépare un ticket existant pour FAIL123
        Ticket ticket = new Ticket();
        ticket.setVehicleRegNumber("FAIL123");
        ticket.setInTime(new Date(System.currentTimeMillis() - 3600_000)); // 1h
        ticket.setParkingSpot(new ParkingSpot(2, ParkingType.CAR, false));
        when(ticketDAO.getTicket("FAIL123")).thenReturn(ticket);

        // 3) on stub updateTicket() pour qu'il échoue
        doReturn(false).when(ticketDAO).updateTicket(any(Ticket.class));

        // 4) appel de la méthode
        parkingService.processExitingVehicle();

        // 5) vérifications :
        //    - on a bien tenté de mettre à jour le ticket
        verify(ticketDAO).updateTicket(any(Ticket.class));
        //    - mais comme ça a échoué, on ne libère PAS la place
        verify(parkingSpotDAO, never()).updateParking(any(ParkingSpot.class));
    }
    
    @Test
    public void testGetNextParkingNumberIfAvailable() throws Exception {
        // 1) on simule la saisie utilisateur pour le type CAR
        when(inputReaderUtil.readSelection()).thenReturn(1);  // 1 = CAR

        // 2) on stub la DAO pour renvoyer la place 1 disponible
        when(parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR)).thenReturn(1);

        // 3) on appelle la méthode sous test
        ParkingSpot spot = parkingService.getNextParkingNumberIfAvailable();

        // 4) on vérifie le résultat
        assertNotNull(spot, "Le spot ne doit pas être null");
        assertEquals(1, spot.getId(), "L'ID du spot doit être 1");
        assertEquals(ParkingType.CAR, spot.getParkingType(), "Le type doit être CAR");
        assertTrue(spot.isAvailable(), "Le spot doit être marqué disponible");
    }
    
    @Test
    public void testGetNextParkingNumberIfAvailableParkingNumberNotFound() throws Exception {
        // 1) on simule la saisie utilisateur pour le type CAR
        when(inputReaderUtil.readSelection()).thenReturn(1);  // 1 = CAR

        // 2) on stub la DAO pour indiquer qu'aucune place n'est disponible
        when(parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR)).thenReturn(0);

        // 3) on appelle la méthode sous test
        ParkingSpot spot = parkingService.getNextParkingNumberIfAvailable();

        // 4) on vérifie le résultat (pas de place disponible)
        assertNull(spot, "Aucun spot ne doit être retourné quand getNextAvailableSlot renvoie 0");
    }
    
    @Test
    public void testGetNextParkingNumberIfAvailableParkingNumberWrongArgument() throws Exception {
        // 1) l'utilisateur saisit un type invalide (3)
        when(inputReaderUtil.readSelection()).thenReturn(3);

        // 2) appel de la méthode sous test
        ParkingSpot spot = parkingService.getNextParkingNumberIfAvailable();

        // 3) on vérifie qu'aucun appel à la DAO n'a été fait et que le résultat est null
        verify(parkingSpotDAO, never()).getNextAvailableSlot(any());
        assertNull(spot, "Aucun spot ne doit être retourné pour une sélection invalide");
    }
}
