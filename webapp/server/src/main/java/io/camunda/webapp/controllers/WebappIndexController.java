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
import io.camunda.webapp.OnAnyWebappUiEnabledCondition;
import io.camunda.zeebe.gateway.rest.config.WebappConfiguration;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@Conditional(OnAnyWebappUiEnabledCondition.class)
public class WebappIndexController {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebappIndexController.class);

  private static final String LOGIN_RESOURCE = "/login";
  private static final String TASKLIST = "tasklist";
  private static final String OPERATE = "operate";
  private static final String ADMIN = "admin";
  private static final String NON_SPA_SEGMENTS =
      "(?:assets|client-config\\.js|custom\\.css|favicon\\.ico)";

  private final ServletContext context;

  private final WebappConfiguration webappConfiguration;

  private final CamundaSecurityLibraryProperties securityProperties;

  private final Set<String> enabledApps;

  public WebappIndexController(
      final ServletContext context,
      @Autowired(required = false) final WebappConfiguration webappConfiguration,
      @Autowired(required = false) final CamundaSecurityLibraryProperties securityProperties,
      final Environment environment) {
    this.context = context;
    this.webappConfiguration =
        webappConfiguration != null ? webappConfiguration : new WebappConfiguration();
    this.securityProperties = securityProperties;
    enabledApps = OnAnyWebappUiEnabledCondition.enabledWebapps(environment);
  }

  @GetMapping({"/tasklist", "/tasklist/", "/tasklist/index.html"})
  public String tasklist(final Model model) {
    return renderShell(TASKLIST, model);
  }

  @GetMapping({"/operate", "/operate/", "/operate/index.html"})
  public String operate(final Model model) {
    return renderShell(OPERATE, model);
  }

  @GetMapping({"/admin", "/admin/", "/admin/index.html"})
  public String admin(final Model model) {
    return renderShell(ADMIN, model);
  }

  /**
   * Forwards SPA routes to index.html, except resource paths under the app segment.
   *
   * <p>{@code assets}, {@code custom.css}, and {@code favicon.ico} moved to the servlet root, and
   * {@code client-config.js} is not served by this module. Excluding their {@code /<app>/...}
   * locations ensures such requests are never answered with the SPA shell and a {@code text/html}
   * content type. The end anchor excludes only exact segment names, so routes such as {@code
   * /tasklist/assets-overview} still reach the shell.
   *
   * <p>The app segment stays a literal in each mapping rather than a path variable: {@code
   * PhysicalTenantRequestMappingHandlerMapping} only registers the {@code /physical-tenants/...}
   * sibling of a route whose pattern starts with a literal webapp root.
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
  public String forwardToTasklist(final HttpServletRequest request) {
    return forwardToShell(TASKLIST, request);
  }

  @GetMapping(
      value = {
        "/operate/{path:^(?!" + NON_SPA_SEGMENTS + "$).+}",
        "/operate/{path:^(?!" + NON_SPA_SEGMENTS + "$).+}/**"
      })
  public String forwardToOperate(final HttpServletRequest request) {
    return forwardToShell(OPERATE, request);
  }

  @GetMapping(
      value = {
        "/admin/{path:^(?!" + NON_SPA_SEGMENTS + "$).+}",
        "/admin/{path:^(?!" + NON_SPA_SEGMENTS + "$).+}/**"
      })
  public String forwardToAdmin(final HttpServletRequest request) {
    return forwardToShell(ADMIN, request);
  }

  private String renderShell(final String app, final Model model) {
    requireEnabled(app);
    // The route tree already includes the app segment (/tasklist, /operate, /admin), so the router
    // basepath is the servlet root. Root-level document requests remain owned by the legacy
    // controllers.
    model.addAttribute("baseName", context.getContextPath() + "/");
    model.addAttribute("contextPath", context.getContextPath());
    model.addAttribute("isEnterprise", webappConfiguration.isEnterprise());
    final var saas = securityProperties != null ? securityProperties.getSaas() : null;
    model.addAttribute("organizationId", nullToEmpty(SaasConfigurationHelper.organizationId(saas)));
    model.addAttribute("clusterId", nullToEmpty(SaasConfigurationHelper.clusterId(saas)));
    return "webapp/index";
  }

  private String forwardToShell(final String app, final HttpServletRequest request) {
    requireEnabled(app);
    if (webappConfiguration.isLoginDelegated() && isNotLoggedIn()) {
      return saveRequestAndRedirectToLogin(request);
    } else {
      return "forward:/" + app + "/";
    }
  }

  private void requireEnabled(final String app) {
    if (!enabledApps.contains(app)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
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
