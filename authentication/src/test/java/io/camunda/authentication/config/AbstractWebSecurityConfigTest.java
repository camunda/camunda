/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static com.google.common.net.HttpHeaders.CACHE_CONTROL;
import static com.google.common.net.HttpHeaders.CONTENT_SECURITY_POLICY;
import static com.google.common.net.HttpHeaders.CONTENT_SECURITY_POLICY_REPORT_ONLY;
import static com.google.common.net.HttpHeaders.CROSS_ORIGIN_EMBEDDER_POLICY;
import static com.google.common.net.HttpHeaders.CROSS_ORIGIN_OPENER_POLICY;
import static com.google.common.net.HttpHeaders.CROSS_ORIGIN_RESOURCE_POLICY;
import static com.google.common.net.HttpHeaders.EXPIRES;
import static com.google.common.net.HttpHeaders.PERMISSIONS_POLICY;
import static com.google.common.net.HttpHeaders.PRAGMA;
import static com.google.common.net.HttpHeaders.REFERRER_POLICY;
import static com.google.common.net.HttpHeaders.STRICT_TRANSPORT_SECURITY;
import static com.google.common.net.HttpHeaders.X_CONTENT_TYPE_OPTIONS;
import static com.google.common.net.HttpHeaders.X_FRAME_OPTIONS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.config.controllers.TestApiController;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.headers.ContentSecurityPolicyConfig;
import io.camunda.security.configuration.headers.PermissionsPolicyConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Common configuration and setup for all tests of this class. Subclasses should add @SpringBootTest
 * annotations with the desired configuration.
 */
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@ActiveProfiles("consolidated-auth")
public class AbstractWebSecurityConfigTest {

  protected static final String EXPECTED_CSRF_TOKEN_COOKIE_NAME = "X-CSRF-TOKEN";
  protected static final String EXPECTED_CSRF_HEADER_NAME = "X-CSRF-TOKEN";

  /**
   * Wrapper around MockMvc that offers AssertJ-based assertions instead of Hamcrest matchers
   *
   * <p>Allows to write tests like this:
   *
   * <p>
   *
   * <pre>
   * MvcTestResult testResult = mockMvcTester.get().uri("/foo").exchange();
   *
   * assertThat(testResult).hasStatusOk(); // add .debug() to inspect the request/response
   * </pre>
   *
   * <p>For more debugging output, you can add the property
   * logging.level.org.springframework.security=DEBUG to the Spring Boot config
   */
  @Autowired MockMvcTester mockMvcTester;

  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  /**
   * Different types of endpoints that the security config handles specifically
   *
   * <ul>
   *   <li>APIs (Operate internal, V1, V2)
   *   <li>webapp resource
   *   <li>unprotected
   *
   * @return
   */
  protected static String[] getAllDummyEndpoints() {
    return new String[] {
      TestApiController.DUMMY_OPERATE_INTERNAL_API_ENDPOINT,
      TestApiController.DUMMY_V1_API_ENDPOINT,
      TestApiController.DUMMY_V2_API_ENDPOINT,
      TestApiController.DUMMY_WEBAPP_ENDPOINT,
      TestApiController.DUMMY_UNPROTECTED_ENDPOINT
    };
  }

  protected void assertDefaultSecurityHeaders(final MvcTestResult response) {
    assertThat(response)
        .headers()
        .hasValue(X_CONTENT_TYPE_OPTIONS, "nosniff")
        .hasValue(CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate")
        .hasValue(PRAGMA, "no-cache")
        .hasValue(EXPIRES, "0")
        .hasValue(X_FRAME_OPTIONS, "SAMEORIGIN")
        .hasValue(CONTENT_SECURITY_POLICY, ContentSecurityPolicyConfig.DEFAULT_SM_SECURITY_POLICY)
        .hasValue(REFERRER_POLICY, "strict-origin-when-cross-origin")
        .hasValue(CROSS_ORIGIN_OPENER_POLICY, "same-origin-allow-popups")
        .hasValue(CROSS_ORIGIN_EMBEDDER_POLICY, "unsafe-none")
        .hasValue(CROSS_ORIGIN_RESOURCE_POLICY, "same-site")
        .hasValue(STRICT_TRANSPORT_SECURITY, "max-age=31536000")
        .hasValue(PERMISSIONS_POLICY, PermissionsPolicyConfig.DEFAULT_PERMISSIONS_POLICY_VALUE)
        .doesNotContainHeaders(CONTENT_SECURITY_POLICY_REPORT_ONLY);
  }

  protected void assertMissingCsrfToken(final MvcTestResult response) {
    assertThat(response)
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .bodyJson()
        .extractingPath("detail")
        .isEqualTo(
            "Could not verify the provided CSRF token because no token was found to compare.");
  }
}
