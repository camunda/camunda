/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.plugin.engine.rest;

import jakarta.ws.rs.client.ClientRequestContext;
import java.io.IOException;

public interface EngineRestFilter {
  /**
   * Filter method called before a request has been dispatched to the REST-API of a process engine.
   *
   * <p>For details see jakarta.ws.rs.client.ClientRequestFilter.filter(ClientRequestContext).
   *
   * @param requestContext request context.
   * @param engineAlias alias (key in configuration) of the engine the request refers to.
   * @param engineName name of the engine the request refers to.
   * @throws IOException if an I/O exception occurs.
   */
  void filter(ClientRequestContext requestContext, String engineAlias, String engineName)
      throws IOException;
}
