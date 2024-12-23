/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.properties;

import static java.util.Arrays.*;
import static org.apache.commons.lang3.StringUtils.*;
import static org.assertj.core.api.AssertionsForClassTypes.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

public class DeprecatedPropertiesTest {
  private static final List<String> IRRELEVANT_METHODS = List.of("toString", "equals", "hashCode");

  @TestFactory
  Stream<DynamicContainer> shouldDeprecate() {
    return ReflectionSupport.findAllClassesInPackage(
            "io.camunda.zeebe.spring.client.properties",
            c ->
                c.isAnnotationPresent(Deprecated.class)
                    && c.isAnnotationPresent(ConfigurationProperties.class),
            n -> true)
        .stream()
        .flatMap(this::unwrap)
        .map(
            c ->
                DynamicContainer.dynamicContainer(
                    c.getName(),
                    ReflectionSupport.findMethods(
                            c, this::isRelevant, HierarchyTraversalMode.TOP_DOWN)
                        .stream()
                        .map(
                            m ->
                                DynamicTest.dynamicTest(
                                    m.getName() + "()", () -> shouldBeDeprecatedProperty(m)))));
  }

  private boolean isRelevant(final Method method) {
    if (method.getParameterCount() > 0) {
      return false;
    }
    if (IRRELEVANT_METHODS.contains(method.getName())) {
      return false;
    }
    if (Modifier.isPrivate(method.getModifiers())) {
      return false;
    }
    return true;
  }

  private Stream<Class<?>> unwrap(final Class<?> clazz) {
    final List<Class<?>> list =
        new java.util.ArrayList<>(stream(clazz.getClasses()).flatMap(this::unwrap).toList());
    list.add(clazz);
    return list.stream();
  }

  private void shouldBeDeprecatedProperty(final Method getter) {
    assertThat(getter)
        .matches(
            g -> g.isAnnotationPresent(DeprecatedConfigurationProperty.class),
            "Getter is annotated with spring deprecation annotation");
    final DeprecatedConfigurationProperty annotation =
        getter.getAnnotation(DeprecatedConfigurationProperty.class);
    assertThat(annotation)
        .matches(a -> isNotEmpty(a.replacement()), "There is a replacement mentioned");
  }
}
