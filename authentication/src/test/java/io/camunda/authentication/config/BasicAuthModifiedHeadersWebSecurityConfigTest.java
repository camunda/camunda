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

import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      WebSecurityConfig.class,
    },
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=basic",
      "camunda.security.http-headers.content-type-options.enabled=false",
      "camunda.security.http-headers.cache-control.enabled=false",
      "camunda.security.http-headers.frame-options.enabled=false",
      "camunda.security.http-headers.content-security-policy.enabled=true",
      "camunda.security.http-headers.content-security-policy.report-only=true",
      "camunda.security.http-headers.content-security-policy.policy-directives=self; camunda.com",
      "camunda.security.http-headers.referrer-policy.value=NO_REFERRER",
      "camunda.security.http-headers.permissions-policy.value=camera=*",
      "camunda.security.http-headers.cross-origin-opener-policy.value=UNSAFE_NONE",
      "camunda.security.http-headers.cross-origin-embedder-policy.value=UNSAFE_NONE",
      "camunda.security.http-headers.cross-origin-resource-policy.value=CROSS_ORIGIN",
      "camunda.security.http-headers.hsts.max-age-in-seconds=5000000",
      "camunda.security.http-headers.hsts.include-subdomains=true",
      "camunda.security.http-headers.hsts.preload=true"
    })
public class BasicAuthModifiedHeadersWebSecurityConfigTest extends AbstractWebSecurityConfigTest {

  @ParameterizedTest
  @MethodSource("getAllDummyEndpoints")
  public void shouldAddSecurityHeadersOnAllApiAndWebappRequests(final String endpoint) {

    // when
    final MvcTestResult testResult =
        mockMvcTester.get().headers(basicAuthDemo()).uri("https://localhost" + endpoint).exchange();

    // then
    assertThat(testResult).hasStatusOk();
    assertThat(testResult)
        .headers()
        .hasValue(CONTENT_SECURITY_POLICY_REPORT_ONLY, "self; camunda.com")
        .hasValue(REFERRER_POLICY, "no-referrer")
        .hasValue(PERMISSIONS_POLICY, "camera=*")
        .hasValue(CROSS_ORIGIN_OPENER_POLICY, "unsafe-none")
        .hasValue(CROSS_ORIGIN_EMBEDDER_POLICY, "unsafe-none")
        .hasValue(CROSS_ORIGIN_RESOURCE_POLICY, "cross-origin")
        .hasValue(STRICT_TRANSPORT_SECURITY, "max-age=5000000 ; includeSubDomains ; preload")
        .doesNotContainHeaders(
            X_CONTENT_TYPE_OPTIONS,
            CACHE_CONTROL,
            PRAGMA,
            EXPIRES,
            X_FRAME_OPTIONS,
            CONTENT_SECURITY_POLICY);
  }

  protected static HttpHeaders basicAuthDemo() {
    final HttpHeaders headers = new HttpHeaders();

    headers.add(HttpHeaders.AUTHORIZATION, basicAuthentication("demo", "demo"));

    return headers;
  }

  private static String basicAuthentication(final String username, final String password) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
  }
}
