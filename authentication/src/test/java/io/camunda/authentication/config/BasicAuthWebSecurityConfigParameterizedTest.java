/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;

@TestInstance(Lifecycle.PER_CLASS)
public class BasicAuthWebSecurityConfigParameterizedTest {

  @ParameterizedTest
  @MethodSource("getAllInvalidProperties")
  void invalidConfigurationApplicationFailed(final Map<String, Object> invalidProperties) {
    assertThatThrownBy(
            () -> {
              try (final var context =
                  new SpringApplicationBuilder(TestApplication.class)
                      .web(WebApplicationType.NONE)
                      .properties(invalidProperties)
                      .run()) {
                // Should not reach here
              }
            })
        .getCause()
        .hasMessageContaining(
            "Oidc configuration is not supported with `BASIC` authentication method");
  }

  private Stream<Map<String, Object>> getAllInvalidProperties() {
    return Stream.of(
        getInvalidProperties("camunda.security.authentication.oidc.client-id", "test-client"),
        getInvalidProperties("camunda.security.authentication.oidc.client-secret", "test-secret"),
        getInvalidProperties("camunda.security.authentication.oidc.organization-id", "org"),
        getInvalidProperties("camunda.security.authentication.oidc.issuer-uri", "http://uri"),
        getInvalidProperties("camunda.security.authentication.oidc.token-uri", "http://uri"),
        getInvalidProperties("camunda.security.authentication.oidc.jwk-set-uri", "http://uri"),
        getInvalidProperties(
            "camunda.security.authentication.oidc.authorization-uri", "http://uri"),
        getInvalidProperties("camunda.security.authentication.oidc.redirect-uri", "http://uri"),
        getInvalidProperties("camunda.security.authentication.oidc.groups-claim", "group"),
        getInvalidProperties("camunda.security.authentication.oidc.username-claim", "sub1"),
        getInvalidProperties(
            "camunda.security.authentication.oidc.grant-type", "client_credentials"));
  }

  private Map<String, Object> getInvalidProperties(final String property, final String value) {
    final Map<String, Object> properties = new HashMap<>();

    properties.put("spring.main.allow-bean-definition-overriding", true);
    properties.put("camunda.security.authentication.unprotected-api", false);
    properties.put("camunda.security.authentication.method", "basic"); // Empty issuer URI
    properties.put(property, value); // Empty issuer URI
    return properties;
  }

  @ActiveProfiles("consolidated-auth")
  @AutoConfigureMockMvc
  @AutoConfigureWebMvc
  @ComponentScan(basePackages = {"io.camunda.authentication"})
  static class TestApplication {}
}
