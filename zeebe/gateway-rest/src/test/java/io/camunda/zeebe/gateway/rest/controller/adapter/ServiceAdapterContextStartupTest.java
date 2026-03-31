/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.AnnotationUtils;

/**
 * Verifies that every service adapter whose generated controller is conditionally loaded (via
 * {@code @Profile}, {@code @Conditional*}, etc.) is itself conditionally loaded with at least the
 * same guards.
 *
 * <p>Without this, an unconditional adapter will be component-scanned in all configurations, but
 * its constructor dependencies (provided only under the matching profile/condition) will be absent,
 * causing {@code UnsatisfiedDependencyException} at startup.
 *
 * <p>This mirrors the runtime failure observed in MCP-enabled deployments without authentication:
 * the generated authentication controller was gated behind {@code @Profile("consolidated-auth")},
 * but its adapter was not, so the adapter failed to wire {@code CamundaUserService}.
 */
class ServiceAdapterContextStartupTest {

  private static final String ADAPTER_PACKAGE = "io.camunda.zeebe.gateway.rest.controller.adapter";
  private static final String GENERATED_PACKAGE =
      "io.camunda.zeebe.gateway.rest.controller.generated";

  /**
   * Controllers that are conditionally loaded, paired with the condition annotations they carry.
   * When a new generated controller adds {@code @Profile} or {@code @Conditional*}, add it here.
   */
  private static final List<ConditionalController> CONDITIONAL_CONTROLLERS =
      List.of(
          new ConditionalController("GeneratedAuthenticationController", Profile.class),
          new ConditionalController("GeneratedGroupController"));

  /**
   * For each conditionally-loaded generated controller, its corresponding adapter must carry the
   * same condition annotations. If it doesn't, the adapter will be scanned unconditionally and fail
   * to wire in minimal configurations.
   */
  @TestFactory
  Stream<DynamicTest> shouldGuardConditionalAdaptersWithMatchingConditions() {
    return CONDITIONAL_CONTROLLERS.stream()
        .flatMap(ServiceAdapterContextStartupTest::verifyAdapterConditions);
  }

  private static Stream<DynamicTest> verifyAdapterConditions(
      final ConditionalController controller) {
    final String adapterName =
        controller
            .simpleName
            .replace("Generated", "Default")
            .replace("Controller", "ServiceAdapter");
    final Class<?> controllerClass = loadClass(GENERATED_PACKAGE + "." + controller.simpleName);
    final Class<?> adapterClass = loadClass(ADAPTER_PACKAGE + "." + adapterName);

    // Find all condition annotations on the controller (both explicit and discovered)
    final Set<Class<? extends Annotation>> controllerConditions =
        getConditionAnnotations(controllerClass);

    // Also include any explicitly listed conditions (for annotations discovered via meta-annotation
    // search that getConditionAnnotations might miss)
    controllerConditions.addAll(Set.of(controller.requiredConditions));

    return controllerConditions.stream()
        .map(
            conditionAnnotation ->
                DynamicTest.dynamicTest(
                    adapterName + " should have @" + conditionAnnotation.getSimpleName(),
                    () ->
                        assertThat(
                                AnnotationUtils.findAnnotation(adapterClass, conditionAnnotation))
                            .as(
                                "%s must be annotated with @%s (matching its controller %s)",
                                adapterName,
                                conditionAnnotation.getSimpleName(),
                                controller.simpleName)
                            .isNotNull()));
  }

  private static Set<Class<? extends Annotation>> getConditionAnnotations(final Class<?> clazz) {
    return Arrays.stream(clazz.getAnnotations())
        .map(Annotation::annotationType)
        .filter(type -> type == Profile.class || type.getSimpleName().startsWith("ConditionalOn"))
        .collect(Collectors.toSet());
  }

  private static Class<?> loadClass(final String name) {
    try {
      return Class.forName(name);
    } catch (final ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private record ConditionalController(
      String simpleName, Class<? extends Annotation>... requiredConditions) {}
}
