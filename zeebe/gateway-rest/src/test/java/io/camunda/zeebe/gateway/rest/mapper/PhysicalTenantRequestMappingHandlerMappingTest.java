/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.spring.utils.PhysicalTenantContext;
import io.camunda.zeebe.gateway.rest.annotation.ClusterScoped;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo.BuilderConfiguration;
import org.springframework.web.util.pattern.PathPatternParser;

class PhysicalTenantRequestMappingHandlerMappingTest {

  private static final String EXPECTED_PREFIX =
      "/physical-tenants/{" + PhysicalTenantContext.PATH_VARIABLE_PHYSICAL_TENANT_ID + "}";

  private final PhysicalTenantRequestMappingHandlerMapping mapping =
      new PhysicalTenantRequestMappingHandlerMapping();

  // ---- shouldPrefix --------------------------------------------------------

  @Test
  void shouldPrefixWhenNotClusterScoped() {
    // when / then
    assertThat(mapping.shouldPrefix(TenantScopedController.class)).isTrue();
  }

  @Test
  void shouldNotPrefixWhenClusterScoped() {
    // when / then
    assertThat(mapping.shouldPrefix(ClusterScopedController.class)).isFalse();
  }

  @Test
  void shouldPrefixPlainControllerNotAnnotatedWithCamundaRestController() {
    // Webapp index controllers (e.g. OperateIndexController) are plain @Controller beans — they
    // must be prefixed so their routes are also registered under /physical-tenants/{id}/...
    assertThat(mapping.shouldPrefix(PlainController.class)).isTrue();
  }

  @Test
  void shouldNotPrefixNullBeanType() {
    // when / then
    assertThat(mapping.shouldPrefix(null)).isFalse();
  }

  // ---- withPhysicalTenantPrefix / pattern rewriting ------------------------

  static Stream<Arguments> patternCases() {
    return Stream.of(
        // API routes
        Arguments.of("/v2", EXPECTED_PREFIX + "/v2"),
        Arguments.of("/v2/widgets", EXPECTED_PREFIX + "/v2/widgets"),
        Arguments.of("/v2/widgets/{id}", EXPECTED_PREFIX + "/v2/widgets/{id}"),
        // webapp routes
        Arguments.of("/operate", EXPECTED_PREFIX + "/operate"),
        Arguments.of("/operate/processes", EXPECTED_PREFIX + "/operate/processes"),
        Arguments.of("/tasklist", EXPECTED_PREFIX + "/tasklist"),
        Arguments.of("/tasklist/tasks/{id}", EXPECTED_PREFIX + "/tasklist/tasks/{id}"),
        Arguments.of("/admin", EXPECTED_PREFIX + "/admin"),
        Arguments.of("/admin/users", EXPECTED_PREFIX + "/admin/users"),
        Arguments.of("/webapp", EXPECTED_PREFIX + "/webapp"),
        Arguments.of("/webapp/some-route", EXPECTED_PREFIX + "/webapp/some-route"),
        // non-matching: different prefix
        Arguments.of("/v1/widgets", null),
        // non-matching: word boundary guards (no trailing slash → not a root)
        Arguments.of("/v2foo", null),
        Arguments.of("/operatepath", null),
        Arguments.of("/tasklistfoo", null),
        Arguments.of("/", null));
  }

  @ParameterizedTest(name = "[{index}] {0} -> {1}")
  @MethodSource("patternCases")
  void shouldRewriteKnownRootPatterns(final String input, final String expected) {
    // given
    final RequestMappingInfo info = info(input);

    // when
    final RequestMappingInfo prefixed = mapping.withPhysicalTenantPrefix(info);

    // then
    if (expected == null) {
      assertThat(prefixed).isNull();
    } else {
      assertThat(prefixed).isNotNull();
      assertThat(prefixed.getPathPatternsCondition()).isNotNull();
      assertThat(prefixed.getPathPatternsCondition().getPatternValues()).containsExactly(expected);
    }
  }

  // ---- registerHandlerMethod -----------------------------------------------

  static Stream<Arguments> registerCases() {
    return Stream.of(
        Arguments.of(
            "tenant-scoped /v2 keeps original and adds prefixed sibling",
            new TenantScopedController(),
            "/v2/widgets",
            List.of("/v2/widgets", EXPECTED_PREFIX + "/v2/widgets")),
        Arguments.of(
            "cluster-scoped controller keeps only original — no sibling",
            new ClusterScopedController(),
            "/v2/widgets",
            List.of("/v2/widgets")),
        Arguments.of(
            "non-/v2 path on tenant-scoped controller keeps only original",
            new TenantScopedController(),
            "/v1/widgets",
            List.of("/v1/widgets")),
        Arguments.of(
            "plain webapp controller /operate keeps original and adds prefixed sibling",
            new PlainController(),
            "/operate",
            List.of("/operate", EXPECTED_PREFIX + "/operate")),
        Arguments.of(
            "plain webapp controller /tasklist/tasks keeps original and adds prefixed sibling",
            new PlainController(),
            "/tasklist/tasks",
            List.of("/tasklist/tasks", EXPECTED_PREFIX + "/tasklist/tasks")));
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

  @Controller
  private static final class PlainController {
    @SuppressWarnings("unused") // resolved reflectively in registerCases()
    public void handle() {}
  }
}
