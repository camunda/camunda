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
import static com.google.common.net.HttpHeaders.X_CONTENT_TYPE_OPTIONS;
import static com.google.common.net.HttpHeaders.X_FRAME_OPTIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import java.util.Base64;
import java.util.List;
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
      "camunda.security.security-headers.content-type-options-config.enabled=false",
      "camunda.security.security-headers.cache-config.enabled=false",
      "camunda.security.security-headers.frame-options-config.enabled=false",
      "camunda.security.security-headers.content-security-policy-config.enabled=true",
      "camunda.security.security-headers.content-security-policy-config.report-only=true",
      "camunda.security.security-headers.content-security-policy-config.policy-directives=self; camunda.com",
      "camunda.security.security-headers.referrer-policy-config.referrer-policy=NO_REFERRER",
      "camunda.security.security-headers.permissions-policy-config.policy=camera=*",
      "camunda.security.security-headers.cross-origin-opener-policy-config.cross-origin-opener-policy=UNSAFE_NONE",
      "camunda.security.security-headers.cross-origin-embedder-policy-config.cross-origin-embedder-policy=UNSAFE_NONE",
      "camunda.security.security-headers.cross-origin-resource-policy-config.cross-origin-resource-policy=CROSS_ORIGIN"
    })
public class BasicAuthModifiedHeadersWebSecurityConfigTest extends AbstractWebSecurityConfigTest {

  @ParameterizedTest
  @MethodSource("getAllDummyEndpoints")
  public void shouldAddSecurityHeadersOnAllApiAndWebappRequests(String endpoint) {

    // when
    final MvcTestResult testResult =
        mockMvcTester.get().headers(basicAuthDemo()).uri(endpoint).exchange();

    // then
    assertThat(testResult).hasStatusOk();
    assertThat(testResult)
        .headers()
        .contains(
            entry(CONTENT_SECURITY_POLICY_REPORT_ONLY, List.of("self; camunda.com")),
            entry(REFERRER_POLICY, List.of("no-referrer")),
            entry(PERMISSIONS_POLICY, List.of("camera=*")),
            entry(CROSS_ORIGIN_OPENER_POLICY, List.of("unsafe-none")),
            entry(CROSS_ORIGIN_EMBEDDER_POLICY, List.of("unsafe-none")),
            entry(CROSS_ORIGIN_RESOURCE_POLICY, List.of("cross-origin")))
        .doesNotContainKeys(
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

  private static String basicAuthentication(String username, String password) {
    return "Basic " + Base64.getEncoder().encodeToString(("demo:demo").getBytes());
  }
}
