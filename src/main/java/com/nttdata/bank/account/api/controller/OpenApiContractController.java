package com.nttdata.bank.account.api.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes the OpenAPI document that is the source of this service.
 */
@RestController
public class OpenApiContractController {

    /**
     * @return the contract file, unchanged
     */
    @GetMapping(value = "/openapi.yaml", produces = "application/yaml")
    public ResponseEntity<Resource> contract() {
        return ResponseEntity.ok(new ClassPathResource("openapi.yaml"));
    }
}