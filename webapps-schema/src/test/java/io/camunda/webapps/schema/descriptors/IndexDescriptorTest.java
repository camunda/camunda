/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.commons.support.ReflectionSupport;

public class IndexDescriptorTest {

  private static final Pattern INDEX_VERSION_PATTERN =
      Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"); // Major.Minor.Patch

  static Stream<IndexDescriptor> indicesAndTemplates() {
    final List<Class<?>> classes =
        ReflectionSupport.findAllClassesInPackage(
            "io.camunda.webapps.schema.descriptors",
            clazz ->
                IndexDescriptor.class.isAssignableFrom(clazz)
                    && !clazz.isInterface()
                    && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers()),
            clazz -> true);

    return classes.stream()
        .map(
            clazz -> {
              try {
                return (IndexDescriptor)
                    clazz
                        .getDeclaredConstructor(String.class, boolean.class)
                        .newInstance(null, true);
              } catch (final Exception e) {
                throw new RuntimeException("Failed to instantiate " + clazz, e);
              }
            });
  }

  @ParameterizedTest
  @MethodSource("indicesAndTemplates")
  void testIndexAndTemplateVersions(final IndexDescriptor index) {
    assertThat(index.getVersion())
        .as("Testing version for %s", index.getFullQualifiedName())
        .matches(INDEX_VERSION_PATTERN);
  }
}
