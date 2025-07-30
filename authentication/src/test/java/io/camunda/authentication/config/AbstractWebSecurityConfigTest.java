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
import static org.assertj.core.api.Assertions.entry;

import io.camunda.authentication.config.controllers.TestApiController;
import io.camunda.security.configuration.headers.ContentSecurityPolicyConfig;
import io.camunda.security.configuration.headers.PermissionsPolicyConfig;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
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
        .contains(
            entry(X_CONTENT_TYPE_OPTIONS, List.of("nosniff")),
            entry(CACHE_CONTROL, List.of("no-cache, no-store, max-age=0, must-revalidate")),
            entry(PRAGMA, List.of("no-cache")),
            entry(EXPIRES, List.of("0")),
            entry(X_FRAME_OPTIONS, List.of("SAMEORIGIN")),
            entry(
                CONTENT_SECURITY_POLICY,
                List.of(ContentSecurityPolicyConfig.DEFAULT_SM_SECURITY_POLICY)),
            entry(REFERRER_POLICY, List.of("strict-origin-when-cross-origin")),
            entry(CROSS_ORIGIN_OPENER_POLICY, List.of("same-origin-allow-popups")),
            entry(CROSS_ORIGIN_EMBEDDER_POLICY, List.of("unsafe-none")),
            entry(CROSS_ORIGIN_RESOURCE_POLICY, List.of("same-site")),
            entry(STRICT_TRANSPORT_SECURITY, List.of("max-age=31536000")),
            entry(
                PERMISSIONS_POLICY,
                List.of(PermissionsPolicyConfig.DEFAULT_PERMISSIONS_POLICY_VALUE)))
        .doesNotContainKeys(CONTENT_SECURITY_POLICY_REPORT_ONLY);
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
