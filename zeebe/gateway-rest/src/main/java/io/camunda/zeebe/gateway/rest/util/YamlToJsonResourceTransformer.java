/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformer;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

public class YamlToJsonResourceTransformer implements ResourceTransformer {
  private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
  private final ObjectMapper jsonMapper = new ObjectMapper();

  @Override
  public Resource transform(
      final HttpServletRequest request,
      final Resource resource,
      final ResourceTransformerChain transformerChain)
      throws IOException {
    try (final InputStream is = resource.getInputStream()) {
      final Object yamlObject = yamlMapper.readValue(is, Object.class);
      final String json = jsonMapper.writeValueAsString(yamlObject);
      final byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
      return new TransformedResource(resource, jsonBytes);
    }
  }
}
