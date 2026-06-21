/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.RestTest;
import io.camunda.zeebe.gateway.rest.config.PhysicalTenantWebMvcConfig;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

@WebMvcTest(useDefaultFilters = false)
@Import({
  PhysicalTenantWebMvcConfig.class,
  PhysicalTenantPathPatternMatchingTest.TestController.class,
  PhysicalTenantPathPatternMatchingTest.PlainWebappController.class
})
class PhysicalTenantPathPatternMatchingTest extends RestTest {

  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @Autowired private RequestMappingHandlerMapping requestMappingHandlerMapping;

  @Test
  @SuppressWarnings("DataFlowIssue")
  void requestMappingHandlerMappingMustUsePathPatternParser() {
    assertThat(requestMappingHandlerMapping.getPatternParser())
        .as(
            "Spring MVC must be configured with PathPatternParser. "
                + "AntPathMatcher leaves RequestMappingInfo#getPathPatternsCondition() null, "
                + "so PhysicalTenantRequestMappingHandlerMapping would silently skip registering "
                + "the /physical-tenants/{id}/v2/... sibling routes.")
        .isInstanceOf(PathPatternParser.class);
  }

  @Test
  void bothOriginalAndTenantPrefixedRouteShouldResolve() {
    // original route stays reachable
    webClient
        .get()
        .uri("/v2/widgets")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("ok");

    // tenant-prefixed sibling resolves to the same controller method
    webClient
        .get()
        .uri("/physical-tenants/default/v2/widgets")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("ok");
  }

  @Test
  void oldTenantInfixPathShouldNotResolve() {
    // /v2/physical-tenants/{id}/... is no longer registered
    webClient
        .get()
        .uri("/v2/physical-tenants/default/widgets")
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void shouldPrefixPlainWebappControllerRoot() {
    webClient.get().uri("/physical-tenants/default/operate").exchange().expectStatus().isOk();
  }

  @Test
  void shouldServeWebappAssetUnderPhysicalTenantPrefix() {
    // backed by src/test/resources/META-INF/resources/operate/assets/test-asset.js — proves the
    // resolver strips the leading {tenant}/{webapp}/assets segments injected by the '*' wildcard
    webClient
        .get()
        .uri("/physical-tenants/default/operate/assets/test-asset.js")
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void shouldServeWebappFaviconUnderPhysicalTenantPrefix() {
    // backed by src/test/resources/META-INF/resources/operate/favicon.ico
    webClient
        .get()
        .uri("/physical-tenants/default/operate/favicon.ico")
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void shouldNotServeUnknownWebappAssetUnderPhysicalTenantPrefix() {
    webClient
        .get()
        .uri("/physical-tenants/default/operate/assets/does-not-exist.js")
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @CamundaRestController
  static class TestController {
    @GetMapping("/v2/widgets")
    public String widgets() {
      return "ok";
    }
  }

  // Mirrors the dist webapp index controllers: a plain @Controller (NOT @CamundaRestController)
  // mapping a webapp root. Verifies such a controller still gets a PT-prefixed sibling.
  @Controller
  static class PlainWebappController {
    @GetMapping("/operate")
    @ResponseBody
    public String operate() {
      return "ok";
    }
  }
}
