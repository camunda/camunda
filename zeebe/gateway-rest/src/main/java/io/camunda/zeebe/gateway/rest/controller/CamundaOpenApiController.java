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
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.bind.annotation.GetMapping;

@CamundaRestController
public class CamundaOpenApiController {

  @Autowired private ObjectMapper objectMapper;

  private String cachedJsonContent;

  @PostConstruct
  public void init() {
    try {
      final ClassPathResource resource = new ClassPathResource("apidoc/rest-api.yaml");
      final String yamlContent =
          new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

      final var yamlMapper = new Jackson2ObjectMapperBuilder().factory(new YAMLFactory()).build();

      final Object yamlObject = yamlMapper.readValue(yamlContent, Object.class);

      cachedJsonContent = objectMapper.writeValueAsString(yamlObject);

    } catch (final Exception e) {
      throw new RuntimeException("Failed to load and parse OpenAPI YAML file at startup", e);
    }
  }

  @GetMapping(value = "/camunda-api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> loadCamundaApiDocs() {
    if (cachedJsonContent != null) {
      return ResponseEntity.ok(cachedJsonContent);
    } else {
      final ProblemDetail problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.INTERNAL_SERVER_ERROR,
              "OpenAPI documentation not available.",
              "Failed to load YAML file with OpenAPI documentation.");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    }
  }
}
