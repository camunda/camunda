package org.camunda.optimize.plugin.engine.rest;

import javax.ws.rs.client.ClientRequestContext;
import java.io.IOException;

public class AddCustomTokenFilter implements EngineRestFilter {

  @Override
  public void filter(ClientRequestContext requestContext, String engineAlias, String engineName) throws IOException {
    requestContext.getHeaders().add("Custom-Token", "SomeCustomToken");
  }

}
