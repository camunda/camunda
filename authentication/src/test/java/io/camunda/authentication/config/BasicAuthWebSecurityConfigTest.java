/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
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
      "camunda.security.authentication.method=basic"
    })
public class BasicAuthWebSecurityConfigTest extends AbstractWebSecurityConfigTest {

  @ParameterizedTest
  @MethodSource("getAllDummyEndpoints")
  public void shouldAddSecurityHeadersOnAllApiAndWebappRequests(final String endpoint) {

    // when
    final MvcTestResult testResult =
        mockMvcTester.get().headers(basicAuthDemo()).uri(endpoint).exchange();

    // then
    assertThat(testResult).hasStatusOk();
    assertDefaultSecurityHeaders(testResult);
  }

  protected static HttpHeaders basicAuthDemo() {
    final HttpHeaders headers = new HttpHeaders();

    headers.add(HttpHeaders.AUTHORIZATION, basicAuthentication("demo", "demo"));

    return headers;
  }

  private static String basicAuthentication(final String username, final String password) {
    return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
  }
}
