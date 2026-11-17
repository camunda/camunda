/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp.controllers;

import static io.camunda.webapps.util.HttpUtils.REQUESTED_URL;
import static io.camunda.webapps.util.HttpUtils.getRequestedUrl;

import io.camunda.security.configuration.SaasConfigurationHelper;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.spring.utils.ConditionalOnWebappUiEnabled;
import io.camunda.zeebe.gateway.rest.config.WebappConfiguration;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnWebappUiEnabled("tasklist")
public class WebappIndexController {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebappIndexController.class);

  private static final String LOGIN_RESOURCE = "/login";
  private static final String NON_SPA_SEGMENTS =
      "(?:assets|client-config\\.js|custom\\.css|favicon\\.ico)";

  private final ServletContext context;

  private final WebappConfiguration webappConfiguration;

  private final CamundaSecurityLibraryProperties securityProperties;

  public WebappIndexController(
      final ServletContext context,
      @Autowired(required = false) final WebappConfiguration webappConfiguration,
      @Autowired(required = false) final CamundaSecurityLibraryProperties securityProperties) {
    this.context = context;
    this.webappConfiguration =
        webappConfiguration != null ? webappConfiguration : new WebappConfiguration();
    this.securityProperties = securityProperties;
  }

  @GetMapping({"/tasklist", "/tasklist/", "/tasklist/index.html"})
  public String webapp(final Model model) {
    // The route tree already includes /tasklist, so the router basepath is the servlet root. During
    // migration, only /tasklist/** enters this shell; root-level, Operate, and Admin document
    // requests remain owned by the legacy controllers until their server routes are migrated.
    model.addAttribute("baseName", context.getContextPath() + "/");
    model.addAttribute("contextPath", context.getContextPath());
    model.addAttribute("isEnterprise", webappConfiguration.isEnterprise());
    final var saas = securityProperties != null ? securityProperties.getSaas() : null;
    model.addAttribute("organizationId", nullToEmpty(SaasConfigurationHelper.organizationId(saas)));
    model.addAttribute("clusterId", nullToEmpty(SaasConfigurationHelper.clusterId(saas)));
    return "webapp/index";
  }

  /**
   * Forwards SPA routes to index.html, except legacy Tasklist resource paths.
   *
   * <p>{@code assets}, {@code custom.css}, and {@code favicon.ico} moved to the servlet root, while
   * {@code client-config.js} was removed. Excluding their former {@code /tasklist/...} locations
   * ensures stale resource requests return 404 instead of the SPA shell with a {@code text/html}
   * content type. The end anchor excludes only exact segment names, so routes such as {@code
   * /tasklist/assets-overview} still reach the shell.
   *
   * <p>The forward + login-redirect logic is intentionally inlined here rather than reusing the
   * legacy {@code WebappsRequestForwardManager} from {@code dist/}. Once Tasklist/Operate/Admin
   * have all been migrated onto this BFF, the legacy manager and its consumers in {@code dist/} are
   * deleted, leaving this controller as the sole implementation. Bounded duplication during the
   * migration window is an explicit choice.
   */
  @GetMapping(
      value = {
        "/tasklist/{path:^(?!" + NON_SPA_SEGMENTS + "$).+}",
        "/tasklist/{path:^(?!" + NON_SPA_SEGMENTS + "$).+}/**"
      })
  public String forwardToWebapp(final HttpServletRequest request) {
    if (webappConfiguration.isLoginDelegated() && isNotLoggedIn()) {
      return saveRequestAndRedirectToLogin(request);
    } else {
      return "forward:/tasklist/";
    }
  }

  private String saveRequestAndRedirectToLogin(final HttpServletRequest request) {
    final String requestedUrl = getRequestedUrl(request);
    request.getSession(true).setAttribute(REQUESTED_URL, requestedUrl);
    LOGGER.warn(
        "Requested path {}, but not authenticated. Redirect to {}",
        request.getRequestURI().substring(request.getContextPath().length()),
        LOGIN_RESOURCE);
    return "forward:" + LOGIN_RESOURCE;
  }

  private boolean isNotLoggedIn() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication == null
        || (authentication instanceof AnonymousAuthenticationToken)
        || !authentication.isAuthenticated();
  }

  private static String nullToEmpty(final String value) {
    return value != null ? value : "";
  }
}
