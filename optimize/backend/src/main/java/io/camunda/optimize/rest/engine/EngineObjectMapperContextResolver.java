/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Provider
@Produces(MediaType.APPLICATION_JSON)
@Component
public class EngineObjectMapperContextResolver implements ContextResolver<ObjectMapper> {

  private final ObjectMapper engineObjectMapper;

  public EngineObjectMapperContextResolver(
      @Qualifier("engineMapper") final ObjectMapper objectMapper) {
    this.engineObjectMapper = objectMapper;
  }

  @Override
  public ObjectMapper getContext(Class<?> type) {
    return engineObjectMapper;
  }
}
