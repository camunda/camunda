/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.jetty;

import static org.camunda.optimize.jetty.OptimizeResourceConstants.INDEX_PAGE;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;

import jakarta.servlet.http.HttpServletResponse;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.eclipse.jetty.ee10.servlet.ServletContextRequest;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.Callback;
import org.springframework.http.HttpHeaders;

public class CustomErrorHandler extends ErrorHandler {
  @Override
  public boolean handle(final Request request, final Response response, final Callback callback) {
    final String requestUri = ((ServletContextRequest) request).getDecodedPathInContext();

    final boolean isApi = requestUri.startsWith(REST_API_PATH);
    // For pages, checks to see if uri ends in .html or if the uri only has one .
    final boolean notApiOrPage =
        !isApi && (requestUri.endsWith(".html") || requestUri.split("\\.").length == 1);

    if (notApiOrPage && HttpStatus.NOT_FOUND_404 == response.getStatus()) {
      response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
      response.getHeaders().add(HttpHeaders.LOCATION, INDEX_PAGE);
      callback.succeeded();
    } else if (isApi && HttpStatus.METHOD_NOT_ALLOWED_405 == response.getStatus()) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      callback.failed(new OptimizeRuntimeException("Bad request"));
    } else {
      response.setStatus(response.getStatus());
      callback.failed(new OptimizeRuntimeException("CustomErrorHandler " + response.getStatus()));
    }

    return true;
  }
}
