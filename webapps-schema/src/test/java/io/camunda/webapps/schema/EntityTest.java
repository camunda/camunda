/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.SinceVersion;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.reflections.Reflections;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EntityTest {
  private Reflections reflections;
  private ObjectMapper objectMapper;

  @BeforeAll
  void setUp() {
    reflections = new Reflections("io.camunda.webapps.schema.entities");
    objectMapper = new ObjectMapper();
  }

  @TestFactory
  Stream<DynamicTest> factoryForEntityTests() {
    return entityClasses().stream()
        .filter(clazz -> !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers()))
        .map(this::createValidationTest);
  }

  @SuppressWarnings("unchecked")
  private Set<Class<? extends ExporterEntity<?>>> entityClasses() {
    return reflections.getSubTypesOf(ExporterEntity.class).stream()
        .map(clazz -> (Class<? extends ExporterEntity<?>>) clazz)
        .collect(Collectors.toSet());
  }

  private DynamicTest createValidationTest(final Class<? extends ExporterEntity<?>> entityClass) {
    return DynamicTest.dynamicTest(
        "shouldHaveDefaultsForNewFields : " + entityClass.getSimpleName(),
        () -> validateNewFieldsHaveDefaults(entityClass));
  }

  private void validateNewFieldsHaveDefaults(final Class<? extends ExporterEntity<?>> entityClass) {
    final Field[] fields = entityClass.getDeclaredFields();

    for (final Field field : fields) {
      if (field.isAnnotationPresent(SinceVersion.class)
          && !field.getAnnotation(SinceVersion.class).nullable()) {

        Assertions.assertThat(hasValidDefault(field))
            .withFailMessage(
                "Field '%s' in class '%s' introduced in version '%s' must have a default value",
                field.getName(),
                entityClass.getSimpleName(),
                field.getAnnotation(SinceVersion.class).value())
            .isTrue();
      }
    }
  }

  private boolean hasValidDefault(final Field field) {
    try {
      final Object instance = createDefaultInstance(field.getDeclaringClass());
      field.setAccessible(true);
      final Object value = field.get(instance);
      return value != null;
    } catch (final Exception e) {
      return false;
    }
  }

  private Object createDefaultInstance(final Class<?> clazz) throws Exception {
    try {
      return clazz.getDeclaredConstructor().newInstance();
    } catch (final Exception e) {
      // If default constructor fails, try with ObjectMapper
      return objectMapper.readValue("{}", clazz);
    }
  }
}
