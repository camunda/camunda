/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

@AllArgsConstructor
@Provider
@Component
public class OptimizeObjectMapperContextResolver implements ContextResolver<ObjectMapper> {
  private final ObjectMapper optimizeObjectMapper;

  public ObjectMapper getContext(Class<?> type) {
    return optimizeObjectMapper;
  }

}
