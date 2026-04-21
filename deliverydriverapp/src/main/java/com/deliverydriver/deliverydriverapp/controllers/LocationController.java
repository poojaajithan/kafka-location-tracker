package com.deliverydriver.deliverydriverapp.controllers;

import com.deliverydriver.deliverydriverapp.services.KafkaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@RequestMapping("/location")
public class LocationController {

    @Autowired
    private KafkaService kafkaService;

    @PostMapping("/update")
    public ResponseEntity<String> updateLocation(@RequestParam("driverId") String driverId, @RequestBody String location) {
        boolean result = kafkaService.updateLocation(driverId, location); 
        if (result) {
            return ResponseEntity.ok("Location updated successfully");
        } else {
            return ResponseEntity.status(500).body("Failed to update location");
        }
    }

}
