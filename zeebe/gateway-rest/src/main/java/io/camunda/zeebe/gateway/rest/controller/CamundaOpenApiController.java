/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.StandardCharsets;

@CamundaRestController
public class CamundaOpenApiController {

  @GetMapping(value = "/camunda-api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> loadCamundaApiDocs() {
    try {
      final ClassPathResource resource = new ClassPathResource("apidoc/rest-api.yaml");

      final String yamlContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

      final ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
      final Object obj = yamlReader.readValue(yamlContent, Object.class);

      final ObjectMapper jsonWriter = new ObjectMapper();
      final String jsonContent = jsonWriter.writeValueAsString(obj);

      return ResponseEntity.ok(jsonContent);
    } catch (final Exception e) {
      return ResponseEntity.internalServerError()
          .body("{\"error\": \"Failed to load YAML: " + e.getMessage() + "\"}");
    }
  }
}
