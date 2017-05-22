package org.camunda.optimize.jetty;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.http.PathMap;
import org.eclipse.jetty.server.Dispatcher;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import javax.activation.MimeType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import java.awt.*;
import java.io.IOException;

/**
 * @author Askar Akhmerov
 */
public class GzipForwardPatternRule implements Filter {

  private static final String GZIP = ".gz";
  private static final Logger logger = Log.getLogger(GzipForwardPatternRule.class);
  private static final String JS = ".js";
  private static final String WOFF = ".woff";
  private static final String WOFF_MEDIA_TYPE = "application/font-woff";


  @Override
  public void init(FilterConfig filterConfig) throws ServletException {

  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
    HttpServletRequest servletRequest = (HttpServletRequest) request;
    HttpServletResponse servletResponse = (HttpServletResponse) response;
    if (servletRequest.getServletPath().toLowerCase().endsWith(JS)) {
      servletResponse.setHeader(HttpHeader.CONTENT_ENCODING.asString(), "gzip");
      String gzipTarget = servletRequest.getServletPath() + GZIP;
      servletResponse.setContentType("application/javascript");
      Dispatcher dispatcher = (Dispatcher) servletRequest.getServletContext().getRequestDispatcher(gzipTarget);
      try {
        dispatcher.forward(servletRequest, servletResponse);
      } catch (ServletException e) {
        logger.debug(e);
      }

    } else if (servletRequest.getServletPath().toLowerCase().endsWith(WOFF)) {
      filterChain.doFilter(servletRequest, servletResponse);
      servletResponse.setContentType(WOFF_MEDIA_TYPE);
    } else {
      filterChain.doFilter(servletRequest, servletResponse);
    }


  }

  @Override
  public void destroy() {

  }
}
