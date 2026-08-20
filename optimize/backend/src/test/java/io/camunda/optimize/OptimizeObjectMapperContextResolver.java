/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;
import org.springframework.stereotype.Component;

@Provider
@Component
public class OptimizeObjectMapperContextResolver implements ContextResolver<ObjectMapper> {

  private final ObjectMapper optimizeObjectMapper;

  public OptimizeObjectMapperContextResolver(final ObjectMapper optimizeObjectMapper) {
    this.optimizeObjectMapper = optimizeObjectMapper;
  }

  @Override
  public ObjectMapper getContext(final Class<?> type) {
    return optimizeObjectMapper;
  }
}
