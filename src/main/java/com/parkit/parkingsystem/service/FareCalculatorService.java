package com.parkit.parkingsystem.service;

import com.parkit.parkingsystem.constants.Fare;
import com.parkit.parkingsystem.model.Ticket;

public class FareCalculatorService {

    public void calculateFare(Ticket ticket) {
        // Vérification de la validité des horaires d'entrée et de sortie
        if ((ticket.getOutTime() == null) || (ticket.getOutTime().before(ticket.getInTime()))) {
            throw new IllegalArgumentException("Out time provided is incorrect: " + ticket.getOutTime());
        }

        // Calcul du temps en millisecondes
        long inTimeMillis = ticket.getInTime().getTime();
        long outTimeMillis = ticket.getOutTime().getTime();
        long durationMillis = outTimeMillis - inTimeMillis;

        // Vérification pour éviter un calcul erroné
        if (durationMillis < 0) {
            throw new IllegalArgumentException("Out time cannot be before in time.");
        }

        // Conversion de la durée en heures (prise en compte des minutes et secondes)
        double durationInHours = durationMillis / (60.0 * 60.0 * 1000.0);

        // Si la durée est inférieure à 30 minutes, le stationnement est gratuit
        if (durationInHours < 0.5) {
            ticket.setPrice(0);
            return;
        }

        // Calcul du tarif en fonction du type de véhicule
        switch (ticket.getParkingSpot().getParkingType()) {
            case CAR:
                ticket.setPrice(durationInHours * Fare.CAR_RATE_PER_HOUR);
                break;
            case BIKE:
                ticket.setPrice(durationInHours * Fare.BIKE_RATE_PER_HOUR);
                break;
            default:
                throw new IllegalArgumentException("Unknown Parking Typededededede");
        }
    }
}
