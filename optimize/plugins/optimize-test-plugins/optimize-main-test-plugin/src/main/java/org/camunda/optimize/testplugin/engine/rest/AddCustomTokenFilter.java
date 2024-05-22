/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.testplugin.engine.rest;

import jakarta.ws.rs.client.ClientRequestContext;
import java.io.IOException;
import org.camunda.optimize.plugin.engine.rest.EngineRestFilter;

public class AddCustomTokenFilter implements EngineRestFilter {

  @Override
  public void filter(ClientRequestContext requestContext, String engineAlias, String engineName)
      throws IOException {
    requestContext.getHeaders().add("Custom-Token", "SomeCustomToken");
  }
}
