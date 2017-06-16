package org.camunda.optimize.jetty;

import org.eclipse.jetty.server.Dispatcher;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Askar Akhmerov
 */
public class NotFoundErrorHandler extends ErrorHandler {
  private static final String INDEX_PAGE = "/";
  private static final String API_PATH = "/api";

  @Override
  public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
    if (
        !request.getServletPath().startsWith(API_PATH) &&
            HttpServletResponse.SC_NOT_FOUND == response.getStatus()
        ) {
      response.sendRedirect(INDEX_PAGE);
    }
  }

}
