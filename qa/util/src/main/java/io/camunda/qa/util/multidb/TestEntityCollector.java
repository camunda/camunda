/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import io.camunda.qa.util.auth.ClientDefinition;
import io.camunda.qa.util.auth.GroupDefinition;
import io.camunda.qa.util.auth.MappingRuleDefinition;
import io.camunda.qa.util.auth.RoleDefinition;
import io.camunda.qa.util.auth.TestClient;
import io.camunda.qa.util.auth.TestGroup;
import io.camunda.qa.util.auth.TestMappingRule;
import io.camunda.qa.util.auth.TestRole;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.platform.commons.support.ModifierSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestEntityCollector {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestEntityCollector.class);

  public TestEntityCollection collect(final Class<?> testClass) {
    final var users =
        findAnnotatedStaticFieldValues(testClass, UserDefinition.class, TestUser.class);
    final var mappingRules =
        findAnnotatedStaticFieldValues(
            testClass, MappingRuleDefinition.class, TestMappingRule.class);
    final var clients =
        findAnnotatedStaticFieldValues(testClass, ClientDefinition.class, TestClient.class);
    final var groups =
        findAnnotatedStaticFieldValues(testClass, GroupDefinition.class, TestGroup.class);
    final var roles =
        findAnnotatedStaticFieldValues(testClass, RoleDefinition.class, TestRole.class);

    return new TestEntityCollection(users, groups, roles, clients, mappingRules);
  }

  private <T> List<T> findAnnotatedStaticFieldValues(
      final Class<?> testClass,
      final Class<? extends Annotation> annotationClass,
      final Class<T> expectedType) {
    return Arrays.stream(testClass.getDeclaredFields())
        .map(field -> getAnnotatedStaticFieldValue(field, annotationClass, expectedType))
        .filter(Objects::nonNull)
        .toList();
  }

  private static <T> @Nullable T getAnnotatedStaticFieldValue(
      final Field field,
      final Class<? extends Annotation> annotationClass,
      final Class<T> expectedType) {
    try {
      if (field.getAnnotation(annotationClass) == null) {
        // ignore fields that aren't annotated.
        return null;
      }
      if (!ModifierSupport.isStatic(field)) {
        LOGGER.warn(
            "Field {} is annotated as {} but not marked static. It is ignored.",
            field.getName(),
            annotationClass.getName());
        return null;
      }
      if (field.getType() != expectedType) {
        LOGGER.warn(
            "Field {} is annotated as {} but not of type {}. It is ignored.",
            field.getName(),
            annotationClass.getName(),
            expectedType.getName());
        return null;
      }
      field.setAccessible(true);
      return expectedType.cast(field.get(null));
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public record TestEntityCollection(
      List<TestUser> users,
      List<TestGroup> groups,
      List<TestRole> roles,
      List<TestClient> clients,
      List<TestMappingRule> mappingRules) {}
}
