/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.rest.annotation.ClusterScoped;
import io.camunda.zeebe.gateway.rest.context.PhysicalTenantContext;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo.BuilderConfiguration;
import org.springframework.web.util.pattern.PathPatternParser;

class PhysicalTenantRequestMappingHandlerMappingTest {

  private static final String TENANT_VAR =
      "{" + PhysicalTenantContext.PATH_VARIABLE_PHYSICAL_TENANT_ID + "}";
  // Existing API-client scheme: /v2/physical-tenants/{id}/...
  private static final String API_CLIENT_PREFIX = "/v2/physical-tenants/" + TENANT_VAR;
  // Webapp-aligned scheme (D7): /physical-tenant/{id}/v2/...
  private static final String WEBAPP_ALIGNED_PREFIX = "/physical-tenant/" + TENANT_VAR + "/v2";

  private final PhysicalTenantRequestMappingHandlerMapping mapping =
      new PhysicalTenantRequestMappingHandlerMapping();

  // ---- shouldPrefix --------------------------------------------------------

  @Test
  void shouldPrefixWhenAnnotatedWithCamundaRestController() {
    // when / then
    assertThat(mapping.shouldPrefix(TenantScopedController.class)).isTrue();
  }

  @Test
  void shouldNotPrefixWhenClusterScoped() {
    // when / then
    assertThat(mapping.shouldPrefix(ClusterScopedController.class)).isFalse();
  }

  @Test
  void shouldNotPrefixPlainClass() {
    // when / then
    assertThat(mapping.shouldPrefix(Object.class)).isFalse();
  }

  // ---- withPhysicalTenantPrefixes / pattern rewriting ----------------------

  static Stream<Arguments> patternCases() {
    return Stream.of(
        Arguments.of("/v2", List.of(API_CLIENT_PREFIX, WEBAPP_ALIGNED_PREFIX)),
        Arguments.of(
            "/v2/widgets",
            List.of(API_CLIENT_PREFIX + "/widgets", WEBAPP_ALIGNED_PREFIX + "/widgets")),
        Arguments.of(
            "/v2/widgets/{id}",
            List.of(API_CLIENT_PREFIX + "/widgets/{id}", WEBAPP_ALIGNED_PREFIX + "/widgets/{id}")),
        Arguments.of("/v1/widgets", null),
        Arguments.of("/v2foo", null),
        Arguments.of("/", null));
  }

  @ParameterizedTest(name = "[{index}] {0} -> {1}")
  @MethodSource("patternCases")
  void shouldRewriteOnlyV2Patterns(final String input, final List<String> expected) {
    // given
    final RequestMappingInfo info = info(input);

    // when
    final List<RequestMappingInfo> prefixed = mapping.withPhysicalTenantPrefixes(info);

    // then
    if (expected == null) {
      assertThat(prefixed).isEmpty();
    } else {
      // Two prefixed RequestMappingInfo siblings — one per scheme, fixed order.
      assertThat(prefixed).hasSize(2);
      assertThat(prefixed.get(0).getPathPatternsCondition()).isNotNull();
      assertThat(prefixed.get(0).getPathPatternsCondition().getPatternValues())
          .containsExactly(expected.get(0));
      assertThat(prefixed.get(1).getPathPatternsCondition()).isNotNull();
      assertThat(prefixed.get(1).getPathPatternsCondition().getPatternValues())
          .containsExactly(expected.get(1));
    }
  }

  // ---- registerHandlerMethod -----------------------------------------------

  static Stream<Arguments> registerCases() {
    return Stream.of(
        Arguments.of(
            "tenant-scoped /v2 keeps original and adds BOTH prefixed siblings",
            new TenantScopedController(),
            "/v2/widgets",
            List.of(
                "/v2/widgets", API_CLIENT_PREFIX + "/widgets", WEBAPP_ALIGNED_PREFIX + "/widgets")),
        Arguments.of(
            "cluster-scoped controller keeps only original — no siblings",
            new ClusterScopedController(),
            "/v2/widgets",
            List.of("/v2/widgets")),
        Arguments.of(
            "non-/v2 path on tenant-scoped controller keeps only original",
            new TenantScopedController(),
            "/v1/widgets",
            List.of("/v1/widgets")));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("registerCases")
  void shouldKeepOriginalAndPrefixWhenApplicable(
      final String description,
      final Object controller,
      final String path,
      final List<String> expectedPatterns)
      throws Exception {
    // given
    final Method method = controller.getClass().getDeclaredMethod("handle");

    // when
    mapping.registerHandlerMethod(controller, method, info(path));

    // then
    assertThat(registeredPatterns())
        .as(description)
        .containsExactlyInAnyOrderElementsOf(expectedPatterns);
  }

  // ---- helpers -------------------------------------------------------------

  private List<String> registeredPatterns() {
    return mapping.getHandlerMethods().keySet().stream()
        .filter(m -> m.getPathPatternsCondition() != null)
        .flatMap(m -> m.getPathPatternsCondition().getPatternValues().stream())
        .toList();
  }

  private static RequestMappingInfo info(final String... patterns) {
    return RequestMappingInfo.paths(patterns).options(builderConfiguration()).build();
  }

  private static BuilderConfiguration builderConfiguration() {
    final BuilderConfiguration cfg = new BuilderConfiguration();
    cfg.setPatternParser(new PathPatternParser());
    return cfg;
  }

  // ---- fixtures ------------------------------------------------------------

  @CamundaRestController
  private static final class TenantScopedController {
    @SuppressWarnings("unused") // resolved reflectively in registerCases()
    public void handle() {}
  }

  @CamundaRestController
  @ClusterScoped
  private static final class ClusterScopedController {
    @SuppressWarnings("unused") // resolved reflectively in registerCases()
    public void handle() {}
  }
}
