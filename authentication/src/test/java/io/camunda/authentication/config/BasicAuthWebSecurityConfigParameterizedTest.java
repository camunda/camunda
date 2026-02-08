/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.service.ApiServicesExecutorProvider;
import io.camunda.service.UserServices;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@TestInstance(Lifecycle.PER_CLASS)
public class BasicAuthWebSecurityConfigParameterizedTest {

  @Test
  void validConfigurationApplicationStarted() {
    new SpringApplicationBuilder(TestApplication.class)
        .web(WebApplicationType.NONE)
        .properties(getBasicProperties())
        .run();
  }

  @ParameterizedTest
  @MethodSource("getAllInvalidProperties")
  void invalidConfigurationApplicationFailed(final Map<String, Object> invalidProperties) {
    assertThatThrownBy(
            () ->
                new SpringApplicationBuilder(TestApplication.class)
                    .web(WebApplicationType.NONE)
                    .properties(invalidProperties)
                    .run())
        .cause()
        .hasMessageContaining(
            "Oidc configuration is not supported with `BASIC` authentication method");
  }

  private Stream<Map<String, Object>> getAllInvalidProperties() {
    return Stream.of(
        getProperties("camunda.security.authentication.oidc.client-id", "test-client"),
        getProperties("camunda.security.authentication.oidc.client-secret", "test-secret"),
        getProperties("camunda.security.authentication.oidc.organization-id", "org"),
        getProperties("camunda.security.authentication.oidc.issuer-uri", "http://uri"),
        getProperties("camunda.security.authentication.oidc.token-uri", "http://uri"),
        getProperties("camunda.security.authentication.oidc.jwk-set-uri", "http://uri"),
        getProperties("camunda.security.authentication.oidc.authorization-uri", "http://uri"),
        getProperties("camunda.security.authentication.oidc.redirect-uri", "http://uri"),
        getProperties("camunda.security.authentication.oidc.groups-claim", "group"),
        getProperties("camunda.security.authentication.oidc.username-claim", "sub1"),
        getProperties("camunda.security.authentication.oidc.grant-type", "client_credentials"),
        getProperties("camunda.security.authentication.oidc.assertion.kidSource", "certificate"),
        getProperties("camunda.security.authentication.oidc.assertion.kidDigestAlgorithm", "sha1"),
        getProperties("camunda.security.authentication.oidc.assertion.kidEncoding", "hex"),
        getProperties("camunda.security.authentication.oidc.assertion.kidCase", "upper"),
        getProperties(
            "camunda.security.authentication.oidc.client-authentication-method", "private_key_jwt"),
        getProperties(
            "camunda.security.authentication.oidc.assertion.keystore.path",
            "/path/to/keystore.p12"),
        getProperties(
            "camunda.security.authentication.oidc.assertion.keystore.password", "keystorepass"),
        getProperties(
            "camunda.security.authentication.oidc.assertion.keystore.key-alias", "myalias"),
        getProperties(
            "camunda.security.authentication.oidc.assertion.keystore.key-password", "keypass"));
  }

  private Map<String, Object> getProperties(final String property, final String value) {
    final Map<String, Object> properties = getBasicProperties();
    properties.put(property, value);
    return properties;
  }

  private Map<String, Object> getBasicProperties() {
    final Map<String, Object> properties = new HashMap<>();
    properties.put("spring.profiles.active", "consolidated-auth");
    properties.put("spring.main.allow-bean-definition-overriding", true);
    properties.put("camunda.security.authentication.unprotected-api", false);
    properties.put("camunda.security.authentication.method", "basic");
    return properties;
  }

  @AutoConfigureMockMvc
  @AutoConfigureWebMvc
  @ComponentScan(
      basePackages = {"io.camunda.authentication"},
      excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Controller"),
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*OidcFlowTestContext")
      })
  public static class TestApplication {
    @Bean
    public ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    public UserServices userServices() {
      return new UserServices(
          null,
          null,
          null,
          null,
          null,
          new ApiServicesExecutorProvider(ForkJoinPool.commonPool()),
          null);
    }

    @Bean
    public HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
      return new HandlerMappingIntrospector();
    }
  }
}
