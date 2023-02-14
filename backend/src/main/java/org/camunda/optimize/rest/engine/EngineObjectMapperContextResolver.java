/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

@Provider
@Produces(MediaType.APPLICATION_JSON)
@Component
public class EngineObjectMapperContextResolver implements ContextResolver<ObjectMapper> {
  private final ObjectMapper engineObjectMapper;

  public EngineObjectMapperContextResolver(@Qualifier("engineMapper") final ObjectMapper objectMapper) {
    this.engineObjectMapper = objectMapper;
  }

  public ObjectMapper getContext(Class<?> type) {
    return engineObjectMapper;
  }

}
