package dk.dtu.routeplanner.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {
    // when a GET request arrives for /status, call this method
    @GetMapping("/status")
    public String getStatus() {
        return "Route Planner API is running.";
    }
    
}
