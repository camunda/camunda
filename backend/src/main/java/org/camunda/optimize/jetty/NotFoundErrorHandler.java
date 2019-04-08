/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.jetty;

import org.apache.http.HttpStatus;
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
import java.io.IOException;


public class NotFoundErrorHandler extends ErrorHandler {
  private static final String INDEX_PAGE = "/index.html";
  private static final String API_PATH = "/api";
  private static final Logger logger = Log.getLogger(NotFoundErrorHandler.class);

  @Override
  public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {

    response.setHeader(HttpHeader.CONTENT_ENCODING.toString(), null);

    boolean notApiOrPage = !request.getServletPath().startsWith(API_PATH) &&
        (request.getServletPath().endsWith(".html") || request.getServletPath().split("\\.").length == 1);

    if (notApiOrPage && HttpServletResponse.SC_NOT_FOUND == response.getStatus()) {
      response.setStatus(HttpStatus.SC_OK);
      response.setContentType(MimeTypes.Type.TEXT_HTML.toString());
      Dispatcher dispatcher = (Dispatcher) request.getServletContext().getRequestDispatcher(INDEX_PAGE);
      
      try {
        dispatcher.forward(request, response);
      } catch (ServletException e) {
        logger.debug(e);
      }
    } else {
      return;
    }
  }
}
