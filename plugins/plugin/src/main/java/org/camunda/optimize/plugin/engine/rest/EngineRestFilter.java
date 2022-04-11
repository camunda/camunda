/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.plugin.engine.rest;

import javax.ws.rs.client.ClientRequestContext;
import java.io.IOException;

public interface EngineRestFilter {

  /**
   * Filter method called before a request has been dispatched to the REST-API of a process engine.
   * <p>
   * For details see javax.ws.rs.client.ClientRequestFilter.filter(ClientRequestContext).
   *
   * @param requestContext request context.
   * @param engineAlias    alias (key in configuration) of the engine the request refers to.
   * @param engineName     name of the engine the request refers to.
   * @throws IOException if an I/O exception occurs.
   */
  void filter(ClientRequestContext requestContext, String engineAlias, String engineName) throws IOException;
}
