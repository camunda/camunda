/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Provider
@Component
public class OptimizeObjectMapperContextResolver implements ContextResolver<ObjectMapper> {

  private final ObjectMapper optimizeObjectMapper;

  @Override
  public ObjectMapper getContext(Class<?> type) {
    return optimizeObjectMapper;
  }
}
