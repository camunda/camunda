/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.application.commons.identity.PhysicalTenantsConfiguredCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Forwards PT-prefixed static-asset requests to the unprefixed asset URL so Spring Boot's default
 * static-resource handler serves the file. Earlier attempts at registering a parallel {@code
 * SimpleUrlHandlerMapping} (with {@code ResourceHttpRequestHandler} instances) or a {@code
 * WebMvcConfigurer.addResourceHandlers} contribution silently failed to match in this stack; a
 * plain controller {@code "forward:"} return is short and reliable.
 *
 * <p>Each mapping uses {@code {*path}} (PathPattern capture-the-rest syntax) so a single controller
 * method handles arbitrary asset sub-paths under {@code assets/}. The {@code app} path variable is
 * constrained to {@code operate|tasklist|admin}, matching the webapps the PoC recognises.
 *
 * <p>The forwarded URL lands on Spring Boot's default resource handler, which is wired to {@code
 * classpath:/META-INF/resources/{app}/} via {@code spring.web.resources.static-locations} (set in
 * {@code WebappsConfigurationInitializer}). The security pass on the forwarded URL is also already
 * in place: {@code SecurityPathAdapter#unauthenticatedWebappPaths} lists the unprefixed asset paths
 * as permitAll, so the forward doesn't fail an auth check it shouldn't.
 *
 * <p>Favicon takes a dedicated method since it isn't under {@code /assets/}.
 */
@Controller
@Conditional(PhysicalTenantsConfiguredCondition.class)
public class PhysicalTenantWebappAssetController {

  @GetMapping("/physical-tenant/{tenantId}/{app:operate|tasklist|admin}/assets/{*path}")
  public String forwardAsset(
      @PathVariable final String tenantId,
      @PathVariable final String app,
      @PathVariable final String path) {
    return "forward:/" + app + "/assets" + path;
  }

  @GetMapping("/physical-tenant/{tenantId}/{app:operate|tasklist|admin}/favicon.ico")
  public String forwardFavicon(
      @PathVariable final String tenantId, @PathVariable final String app) {
    return "forward:/" + app + "/favicon.ico";
  }
}
