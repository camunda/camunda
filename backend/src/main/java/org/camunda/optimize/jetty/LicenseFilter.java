/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.jetty;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.exceptions.license.OptimizeLicenseException;
import org.camunda.optimize.service.license.LicenseManager;
import org.springframework.context.ApplicationContext;

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
import java.util.Arrays;
import java.util.Set;

import static org.camunda.optimize.jetty.OptimizeResourceConstants.STATIC_RESOURCE_PATH;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.STATUS_WEBSOCKET_PATH;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CCSM_PROFILE;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CLOUD_PROFILE;

@RequiredArgsConstructor
@Slf4j
public class LicenseFilter implements Filter {
  private final LicenseManager licenseManager;
  private final ApplicationContext applicationContext;
  private static final Set<String> EXCLUDED_EXTENSIONS = Set.of("css", "html", "js", "ico");
  private static final Set<String> EXCLUDED_API_CALLS = Set.of(
    "authentication",
    "localization",
    "ui-configuration",
    "license/validate-and-store",
    "license/validate",
    "status",
    "readyz"
  );

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
    HttpServletResponse servletResponse = (HttpServletResponse) response;
    HttpServletRequest servletRequest = (HttpServletRequest) request;

    if (isLicenseCheckNeeded(servletRequest, applicationContext)) {
      try {
        licenseManager.validateLicenseStoredInOptimize();
      } catch (OptimizeLicenseException e) {
        log.warn("Given License is invalid or not available!");
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

  private static boolean isLicenseCheckNeeded(HttpServletRequest servletRequest, ApplicationContext applicationContext) {
    String requestPath = servletRequest.getServletPath().toLowerCase();
    String pathInfo = servletRequest.getPathInfo();

    return !isStaticResource(requestPath)
      && !isRootRequest(requestPath)
      && !isCloudEnvironment(applicationContext)
      && !isExcludedApiPath(pathInfo)
      && !isStatusRequest(requestPath);
  }

  private static boolean isCloudEnvironment(ApplicationContext applicationContext) {
    return Arrays.stream(applicationContext.getEnvironment().getActiveProfiles())
      .anyMatch(profile -> CLOUD_PROFILE.equalsIgnoreCase(profile) || CCSM_PROFILE.equalsIgnoreCase(profile));
  }

  private static boolean isStatusRequest(String requestPath) {
    return requestPath.equals(STATUS_WEBSOCKET_PATH);
  }

  private static boolean isExcludedApiPath(String pathInfo) {
    return pathInfo != null && EXCLUDED_API_CALLS.stream().anyMatch(pathInfo::contains);
  }

  private static boolean isRootRequest(String requestPath) {
    return requestPath.equals("/");
  }

  private static boolean isStaticResource(String requestPath) {
    return requestPath.contains("^" + STATIC_RESOURCE_PATH + "/.+")
      || EXCLUDED_EXTENSIONS.stream().anyMatch(ext -> requestPath.endsWith("." + ext));
  }

  @Override
  public void destroy() {
    // nothing to do here
  }
}
