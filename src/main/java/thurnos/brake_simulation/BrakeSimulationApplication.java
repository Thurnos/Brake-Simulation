package thurnos.brake_simulation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Brake Simulation application.
 * Clean Spring Boot 4 setup: no Admin server, no old MailSender, OAuth2 ready.
 */
@SpringBootApplication
public class BrakeSimulationApplication {

    public static void main(String[] args) {
        SpringApplication.run(BrakeSimulationApplication.class, args);
    }

}