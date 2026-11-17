/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.application.commons.pt.PhysicalTenantSpringWebProviderConfiguration.PhysicalTenantAwareSpringWebMvcProvider;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo.BuilderConfiguration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

class PhysicalTenantSpringWebProviderConfigurationTest {

  private static final String BARE_PATTERN = "/v3/api-docs";
  private static final String PHYSICAL_TENANT_PATTERN =
      "/physical-tenants/{physicalTenantId}/v3/api-docs";

  static Stream<Arguments> registrationOrders() {
    return Stream.of(
        Arguments.of(List.of(BARE_PATTERN, PHYSICAL_TENANT_PATTERN)),
        Arguments.of(List.of(PHYSICAL_TENANT_PATTERN, BARE_PATTERN)));
  }

  @ParameterizedTest(name = "[{index}] registration order: {0}")
  @MethodSource("registrationOrders")
  void shouldResolveEmptyPrefixRegardlessOfRegistrationOrder(final List<String> patterns) {
    // given
    final var provider = providerWith(patterns.toArray(String[]::new));

    // when
    final String prefix = provider.findPathPrefix(apiDocsConfig());

    // then
    assertThat(prefix).isEmpty();
  }

  @Test
  void shouldResolveGenuineNonEmptyPrefixForCustomBasePath() {
    // Guards the inherited (non-PT) behaviour springdoc's original findPathPrefix provides: a
    // real custom base path in front of the api-docs mapping must still resolve, not just the
    // PT-prefixed case this override specifically targets.

    // given
    final var provider = providerWith("/api" + BARE_PATTERN);

    // when
    final String prefix = provider.findPathPrefix(apiDocsConfig());

    // then
    assertThat(prefix).isEqualTo("/api");
  }

  /**
   * Builds a provider backed by a single mocked {@link RequestMappingHandlerMapping} whose {@code
   * getHandlerMethods()} returns the given patterns as an insertion-ordered map — {@code patterns}'
   * order stands in for the registration order that {@code findPathPrefix} used to depend on.
   */
  private static PhysicalTenantAwareSpringWebMvcProvider providerWith(final String... patterns) {
    final Map<RequestMappingInfo, HandlerMethod> handlerMethods = new LinkedHashMap<>();
    for (final String pattern : patterns) {
      handlerMethods.put(requestMappingInfo(pattern), handlerMethod());
    }

    final RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
    when(mapping.getHandlerMethods()).thenReturn(handlerMethods);

    final ApplicationContext applicationContext = mock(ApplicationContext.class);
    when(applicationContext.getBeansOfType(RequestMappingHandlerMapping.class))
        .thenReturn(Map.of("requestMappingHandlerMapping", mapping));

    final var provider = new PhysicalTenantAwareSpringWebMvcProvider(Optional.empty());
    provider.setApplicationContext(applicationContext);
    return provider;
  }

  private static SpringDocConfigProperties apiDocsConfig() {
    final SpringDocConfigProperties config = new SpringDocConfigProperties();
    config.getApiDocs().setPath(BARE_PATTERN);
    return config;
  }

  private static RequestMappingInfo requestMappingInfo(final String pattern) {
    final BuilderConfiguration configuration = new BuilderConfiguration();
    configuration.setPatternParser(new PathPatternParser());
    return RequestMappingInfo.paths(pattern).options(configuration).build();
  }

  private static HandlerMethod handlerMethod() {
    try {
      final Method method = FixtureController.class.getDeclaredMethod("handle");
      return new HandlerMethod(new FixtureController(), method);
    } catch (final NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final class FixtureController {
    @SuppressWarnings("unused") // resolved reflectively in handlerMethod()
    public void handle() {}
  }
}
