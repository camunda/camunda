package org.camunda.optimize.plugin.engine.rest;

import java.io.IOException;

import javax.annotation.Resource;
import javax.ws.rs.client.ClientRequestContext;

@Resource
public interface EngineRestFilter {

  /**
   * Filter method called before a request has been dispatched to the REST-API of a process engine.
   * 
   * For details see javax.ws.rs.client.ClientRequestFilter.filter(ClientRequestContext).
   *
   * @param requestContext request context.
   * @param engineAlias alias (key in configuration) of the engine the request refers to.
   * @param engineName name of the engine the request refers to.
   * @throws IOException if an I/O exception occurs.
   */
  void filter(ClientRequestContext requestContext, String engineAlias, String engineName) throws IOException;
}
