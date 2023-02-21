/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.jetty;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Dispatcher;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import java.io.IOException;

import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;

public class NotFoundErrorHandler extends ErrorHandler {
  private static final String INDEX_PAGE = "/index.html";
  private static final Logger logger = Log.getLogger(NotFoundErrorHandler.class);

  @Override
  public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
    throws IOException {

    response.setHeader(HttpHeader.CONTENT_ENCODING.toString(), null);

    String requestUri = request.getRequestURI();
    boolean notApiOrPage = !requestUri.startsWith(REST_API_PATH) &&
      (requestUri.endsWith(".html") || requestUri.split("\\.").length == 1);

    if (notApiOrPage && Response.Status.NOT_FOUND.getStatusCode() == response.getStatus()) {
      response.setStatus(Response.Status.OK.getStatusCode());
      response.setContentType(MimeTypes.Type.TEXT_HTML.toString());
      Dispatcher dispatcher = (Dispatcher) ((Request) request).getErrorContext().getRequestDispatcher(INDEX_PAGE);

      try {
        dispatcher.forward(request, response);
      } catch (ServletException e) {
        logger.debug(e);
      }
    }
  }
}
