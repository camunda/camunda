/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.jetty;

import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Dispatcher;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotFoundErrorHandler extends ErrorHandler {
  private static final String INDEX_PAGE = "/index.html";
  private static final Logger logger = LoggerFactory.getLogger(NotFoundErrorHandler.class);

  @Override
  public void handle(
      final String target,
      final Request baseRequest,
      final HttpServletRequest request,
      final HttpServletResponse response)
      throws IOException {

    response.setHeader(HttpHeader.CONTENT_ENCODING.toString(), null);

    final String requestUri = request.getRequestURI();
    final boolean notApiOrPage =
        !requestUri.startsWith(REST_API_PATH)
            && (requestUri.endsWith(".html") || requestUri.split("\\.").length == 1);

    if (notApiOrPage && Response.Status.NOT_FOUND.getStatusCode() == response.getStatus()) {
      response.setStatus(Response.Status.OK.getStatusCode());
      response.setContentType(MimeTypes.Type.TEXT_HTML.toString());
      final Dispatcher dispatcher =
          (Dispatcher) ((Request) request).getErrorContext().getRequestDispatcher(INDEX_PAGE);

      try {
        dispatcher.forward(request, response);
      } catch (final ServletException e) {
        logger.debug("Exception", e);
      }
    }
  }
}
