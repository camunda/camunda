/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.jetty;

import com.google.common.collect.Sets;
import org.camunda.optimize.service.exceptions.license.OptimizeLicenseException;
import org.camunda.optimize.service.license.LicenseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public class LicenseFilter implements Filter {

  private static final Logger logger = LoggerFactory.getLogger(LicenseFilter.class);

  private LicenseManager licenseManager;

  private SpringAwareServletConfiguration awareDelegate;

  private static final Set<String> EXCLUDED_EXTENSIONS = Sets.newHashSet("css", "html", "js", "ico");

  private static final List<String> EXCLUDED_API_CALLS = List.of(
    "authentication",
    "localization",
    "ui-configuration",
    "license/validate-and-store",
    "license/validate",
    "status",
    "readyz"
  );

  private static final List<String> SSO_PATHS = List.of("sso/auth0", "sso-callback");

  public LicenseFilter(SpringAwareServletConfiguration awareDelegate) {
    this.awareDelegate = awareDelegate;
  }

  @Override
  public void init(FilterConfig filterConfig) {
    // nothing to do here
  }

  /**
   * Before the user can access the Optimize APIs a license check is performed.
   * Whenever there is an invalid or no license, backend returns status code 403.
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws
                                                                                            IOException,
                                                                                            ServletException {
    setLicenseManager();
    HttpServletResponse servletResponse = (HttpServletResponse) response;
    HttpServletRequest servletRequest = (HttpServletRequest) request;

    if (isLicenseCheckNeeded(servletRequest)) {
      try {
        licenseManager.validateLicenseStoredInOptimize();
      } catch (OptimizeLicenseException e) {
        logger.warn("Given License is invalid or not available!");
        constructForbiddenResponse(servletResponse, e);
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private void constructForbiddenResponse(HttpServletResponse servletResponse, OptimizeLicenseException ex)
    throws IOException {
    servletResponse.getWriter().write("{\"errorCode\": \"" + ex.getErrorCode() + "\"}");
    servletResponse.setContentType("application/json");
    servletResponse.setCharacterEncoding("UTF-8");
    servletResponse.setStatus(Response.Status.FORBIDDEN.getStatusCode());
  }

  private void setLicenseManager() {
    if (licenseManager == null) {
      licenseManager = awareDelegate.getApplicationContext().getBean(LicenseManager.class);
    }
  }

  private boolean isLicenseCheckNeeded(HttpServletRequest servletRequest) {
    String requestPath = servletRequest.getServletPath().toLowerCase();
    String pathInfo = servletRequest.getPathInfo();

    return !isStaticResource(requestPath)
      && !isRootRequest(requestPath)
      && !isSsoPath(requestPath)
      && !isExcludedApiPath(pathInfo)
      && !isStatusRequest(requestPath);
  }

  // TODO to be removed with disabling this filter in cloud environments, see OPT-5317
  private boolean isSsoPath(final String requestPath) {
    return requestPath != null && SSO_PATHS.stream().anyMatch(requestPath::contains);
  }

  private static boolean isStatusRequest(String requestPath) {
    return requestPath.equals("/ws/status");
  }

  private static boolean isExcludedApiPath(String pathInfo) {
    return pathInfo != null && EXCLUDED_API_CALLS.stream().anyMatch(pathInfo::contains);
  }

  private static boolean isRootRequest(String requestPath) {
    return requestPath.equals("/");
  }

  private static boolean isStaticResource(String requestPath) {
    return requestPath.contains("^/static/.+")
      || EXCLUDED_EXTENSIONS.stream().anyMatch(ext -> requestPath.endsWith("." + ext));
  }

  @Override
  public void destroy() {
    // nothing to do here
  }
}
