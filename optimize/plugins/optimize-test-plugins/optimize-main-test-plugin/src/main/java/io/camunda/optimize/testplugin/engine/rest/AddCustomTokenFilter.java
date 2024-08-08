/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.testplugin.engine.rest;

import io.camunda.optimize.plugin.engine.rest.EngineRestFilter;
import jakarta.ws.rs.client.ClientRequestContext;
import java.io.IOException;

public class AddCustomTokenFilter implements EngineRestFilter {

  @Override
  public void filter(ClientRequestContext requestContext, String engineAlias, String engineName)
      throws IOException {
    requestContext.getHeaders().add("Custom-Token", "SomeCustomToken");
  }
}
