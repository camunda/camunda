/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.commons.support.ReflectionSupport;

class SearchColumnTest {
  private static final Set<Class<?>> SEARCH_COLUMNS =
      new HashSet<>(
          ReflectionSupport.findAllClassesInPackage(
              "io.camunda.db.rdbms.sql.columns",
              c -> {
                final var interfaces = c.getInterfaces();
                return Arrays.asList(interfaces).contains(SearchColumn.class);
              },
              ignored -> true));

  private static List<Object[]> provideSearchColumns() {
    return SEARCH_COLUMNS.stream()
        .filter(Class::isEnum) // Ensure the class is an enum
        .flatMap(
            clazz -> {
              try {
                // Use reflection to call the `values()` method on the enum class
                final Object[] enumConstants = clazz.getEnumConstants();
                return Arrays.stream(enumConstants)
                    .map(e -> new Object[] {clazz.getSimpleName(), (SearchColumn<?>) e});
              } catch (final Exception e) {
                throw new RuntimeException("Failed to retrieve enum values for class: " + clazz, e);
              }
            })
        .toList();
  }

  private static List<Class> provideSearchColumnsTypes() {
    return SEARCH_COLUMNS.stream()
        .filter(Class::isEnum) // Ensure the class is an enum
        .flatMap(
            clazz -> {
              try {
                // Use reflection to call the `values()` method on the enum class
                final Object[] enumConstants = clazz.getEnumConstants();
                return Arrays.stream(enumConstants)
                    .map(
                        e -> {
                          final var c = (SearchColumn<?>) e;
                          try {
                            return c.getPropertyType();
                          } catch (final Exception ex) {
                            return String.class;
                          }
                        });
              } catch (final Exception e) {
                throw new RuntimeException("Failed to retrieve enum values for class: " + clazz, e);
              }
            })
        .toList();
  }

  @ParameterizedTest(name = "{0}#{1}")
  @MethodSource("provideSearchColumns")
  void testAllPropertiesWork(final String className, final SearchColumn<?> column) {
    assertDoesNotThrow(() -> column.getEntityClass().getMethod(column.property()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("provideSearchColumnsTypes")
  void testAllTypesHaveConverters(final Class typeClass) {
    assertDoesNotThrow(() -> SearchColumn.getConverter(typeClass));
  }

  // for each entity class check if it can be converted from an to the specific type
}
