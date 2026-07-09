/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import java.lang.reflect.RecordComponent;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * ArchUnit rule ensuring that RDBMS entity mappers call {@code NullSafeStrings.nullToEmpty()} for
 * every non-{@link Nullable @Nullable} {@link String} record component of the entity they map to.
 *
 * <p>Oracle stores empty strings as {@code NULL}. Without this guard, a field that was an empty
 * string when persisted will be returned as {@code null} when read back, causing a {@link
 * NullPointerException} in the record's compact constructor or silently leaking {@code null} into
 * the API layer.
 *
 * <p>The rule groups constructor calls and {@code nullToEmpty()} calls by the method that contains
 * them (via {@code getOrigin()}), then checks per method that the {@code nullToEmpty()} call count
 * is at least the number of non-{@code @Nullable} {@code String} components in the entity records
 * that method directly constructs. Nested record types populated through MyBatis XML result maps
 * are <em>not</em> counted, because those fields are handled by {@code
 * NullToEmptyStringTypeHandler} in the XML, not by {@code nullToEmpty()} in Java.
 *
 * <p>The check is per-method rather than per-class, which prevents a method that wraps extra fields
 * from masking a sibling method that wraps none. It remains a count-based heuristic and does not
 * perform data-flow analysis, so it cannot detect a method that wraps one field twice while
 * omitting another of the same type.
 *
 * <p>When adding a new {@code *EntityMapper}, wrap every non-{@code @Nullable} {@code String} field
 * read from the {@code DbModel} with {@code NullSafeStrings.nullToEmpty(...)}.
 */
@AnalyzeClasses(
    packages = "io.camunda.db.rdbms.read.mapper",
    importOptions = DoNotIncludeTestsOrTestJars.class)
class RdbmsEntityMapperOracleNullSafetyArchTest {

  static final String NULL_SAFE_STRINGS_CLASS = "io.camunda.db.rdbms.read.NullSafeStrings";
  static final String NULL_TO_EMPTY_METHOD = "nullToEmpty";
  static final String ENTITY_PACKAGE_PREFIX = "io.camunda.search.entities";

  @ArchTest
  static final ArchRule
      ENTITY_MAPPERS_MUST_CALL_NULL_TO_EMPTY_FOR_EACH_NON_NULLABLE_STRING_COMPONENT =
          ArchRuleDefinition.classes()
              .that()
              .haveSimpleNameEndingWith("EntityMapper")
              .should(useNullToEmptyForAllNonNullableStringComponents())
              .because(
                  "Oracle stores empty strings as NULL; entity mappers must call "
                      + "NullSafeStrings.nullToEmpty() for each non-@Nullable String component "
                      + "to prevent NullPointerException or null leakage on Oracle databases");

  private static ArchCondition<JavaClass> useNullToEmptyForAllNonNullableStringComponents() {
    return new ArchCondition<>(
        "call nullToEmpty for every non-@Nullable String component of directly-constructed"
            + " entity records") {
      @Override
      public void check(final JavaClass mapperClass, final ConditionEvents events) {
        // Group entity constructor calls by the method that makes them.
        //
        // Two cases per call target:
        //   1. Direct record construction (new FooEntity(...)) — class IS a record, use it.
        //   2. Builder inner class (new FooEntity.Builder()) — resolve to outer entity record.
        //
        // Nested record types populated via MyBatis XML result maps are NOT included here,
        // because the mapper never calls their constructors directly.
        final Map<JavaCodeUnit, Set<String>> constructorsByMethod =
            mapperClass.getConstructorCallsFromSelf().stream()
                .filter(c -> c.getTarget().getOwner().getName().startsWith(ENTITY_PACKAGE_PREFIX))
                .collect(
                    groupingBy(
                        JavaCall::getOrigin,
                        mapping(c -> c.getTarget().getOwner().getName(), toSet())));

        if (constructorsByMethod.isEmpty()) {
          return;
        }

        // Group nullToEmpty() calls by the method that makes them.
        final Map<JavaCodeUnit, Long> nullToEmptyByMethod =
            mapperClass.getMethodCallsFromSelf().stream()
                .filter(
                    c ->
                        c.getTarget().getOwner().getName().equals(NULL_SAFE_STRINGS_CLASS)
                            && c.getTarget().getName().equals(NULL_TO_EMPTY_METHOD))
                .collect(groupingBy(JavaCall::getOrigin, counting()));

        // Check each method independently.
        for (final Map.Entry<JavaCodeUnit, Set<String>> entry : constructorsByMethod.entrySet()) {
          final JavaCodeUnit method = entry.getKey();
          final Set<String> rawNames = entry.getValue();

          final Set<Class<?>> entityRecords = new HashSet<>();
          for (final String name : rawNames) {
            try {
              final Class<?> cls = Class.forName(name);
              if (cls.isRecord()) {
                entityRecords.add(cls);
              } else if (name.contains("$")) {
                // Likely a Builder or other inner helper — resolve to outer entity.
                final String outerName = name.substring(0, name.lastIndexOf('$'));
                final Class<?> outerCls = Class.forName(outerName);
                if (outerCls.isRecord()) {
                  entityRecords.add(outerCls);
                }
              }
            } catch (final ClassNotFoundException | LinkageError e) {
              events.add(
                  violated(
                      mapperClass,
                      String.format(
                          "Mapper '%s', method '%s': could not load entity class '%s' to count"
                              + " non-@Nullable String components: %s",
                          mapperClass.getSimpleName(), method.getName(), name, e.getMessage())));
            }
          }

          int requiredCount = 0;
          for (final Class<?> record : entityRecords) {
            for (final RecordComponent component : record.getRecordComponents()) {
              if (component.getType() == String.class && !isNullable(component)) {
                requiredCount++;
              }
            }
          }

          if (requiredCount == 0) {
            continue;
          }

          final long actualCount = nullToEmptyByMethod.getOrDefault(method, 0L);

          if (actualCount < requiredCount) {
            final Set<String> recordNames =
                entityRecords.stream().map(Class::getSimpleName).collect(Collectors.toSet());
            events.add(
                violated(
                    mapperClass,
                    String.format(
                        "Mapper '%s', method '%s' calls nullToEmpty %d time(s) but"
                            + " directly-constructed entity record(s) %s have %d non-@Nullable"
                            + " String component(s) — add NullSafeStrings.nullToEmpty() for each"
                            + " String field read from the DB model; Oracle stores empty strings"
                            + " as NULL",
                        mapperClass.getSimpleName(),
                        method.getName(),
                        actualCount,
                        recordNames,
                        requiredCount)));
          }
        }
      }
    };
  }

  private static boolean isNullable(final RecordComponent component) {
    return component.isAnnotationPresent(Nullable.class)
        || component.getAnnotatedType().isAnnotationPresent(Nullable.class);
  }
}
