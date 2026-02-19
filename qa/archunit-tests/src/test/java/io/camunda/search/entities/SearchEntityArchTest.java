/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ArchUnit rules for search entity records in {@code io.camunda.search.entities}.
 *
 * <p>These rules enforce:
 *
 * <ol>
 *   <li>All top-level types in the package (excluding enums and interfaces) must be Java {@code
 *       record}s.
 *   <li>Every record with collection-type fields ({@link List}, {@link Set}, {@link Map}, {@link
 *       Collection}) must declare a compact constructor that defaults those fields to empty mutable
 *       instances (e.g. {@code new ArrayList<>()}, {@code new HashSet<>()}, {@code new
 *       HashMap<>()}). This is required because MyBatis hydrates collection-mapped fields by
 *       calling mutating methods on the existing instance; immutable defaults such as {@code
 *       List.of()} would cause {@code UnsupportedOperationException} at runtime.
 * </ol>
 */
@AnalyzeClasses(
    packages = "io.camunda.search.entities",
    importOptions = ImportOption.DoNotIncludeTests.class)
public final class SearchEntityArchTest {

  static final Set<Class<?>> COLLECTION_TYPES =
      Set.of(List.class, Set.class, Map.class, Collection.class);

  /**
   * Every concrete top-level type inside {@code io.camunda.search.entities} must be a Java {@code
   * record}. Enums and interfaces are excluded.
   */
  @ArchTest
  static final ArchRule SEARCH_ENTITIES_MUST_BE_RECORDS =
      ArchRuleDefinition.classes()
          .that()
          .resideInAPackage("io.camunda.search.entities")
          .and()
          .areNotEnums()
          .and()
          .areNotInterfaces()
          .and()
          .areTopLevelClasses()
          .should(
              new ArchCondition<>("be a Java record") {
                @Override
                public void check(final JavaClass item, final ConditionEvents events) {
                  if (!item.isRecord()) {
                    events.add(
                        violated(
                            item,
                            String.format(
                                "Class '%s' in io.camunda.search.entities is not a Java record",
                                item.getName())));
                  }
                }
              })
          .because(
              "search entities should be immutable data carriers (records) "
                  + "to ensure consistency across API gateways");

  /**
   * Every record in {@code io.camunda.search.entities} that declares collection-type fields must
   * have a compact constructor that defaults them to empty mutable instances when {@code null} is
   * passed. This is verified by reflectively instantiating the record with {@code null} for all
   * collection parameters and checking that the resulting fields are non-null, empty, and mutable.
   */
  @ArchTest
  static final ArchRule COLLECTION_FIELDS_MUST_HAVE_MUTABLE_DEFAULTS =
      ArchRuleDefinition.classes()
          .that()
          .resideInAPackage("io.camunda.search.entities..")
          .and()
          .areRecords()
          .should(
              new ArchCondition<>(
                  "initialize collection-type fields with mutable defaults in a compact constructor") {
                @Override
                public void check(final JavaClass item, final ConditionEvents events) {
                  final var collectionFields =
                      item.getFields().stream()
                          .filter(SearchEntityArchTest::isCollectionField)
                          .toList();

                  if (collectionFields.isEmpty()) {
                    return;
                  }

                  // Reflectively instantiate the record with null for all reference-type
                  // parameters and default values for primitives.
                  final Object instance;
                  try {
                    instance = instantiateRecordWithNulls(item);
                  } catch (final Exception e) {
                    events.add(
                        violated(
                            item,
                            String.format(
                                "Record '%s': could not reflectively instantiate to verify "
                                    + "collection defaults: %s",
                                item.getSimpleName(), e.getMessage())));
                    return;
                  }

                  for (final JavaField field : collectionFields) {
                    try {
                      final var reflectField =
                          instance.getClass().getDeclaredField(field.getName());
                      reflectField.setAccessible(true);
                      final Object value = reflectField.get(instance);

                      if (value == null) {
                        events.add(
                            violated(
                                item,
                                String.format(
                                    "Record '%s': collection field '%s' (type %s) is null when "
                                        + "constructed with null — add a compact constructor that "
                                        + "assigns an empty mutable default "
                                        + "(e.g. new ArrayList<>(), new HashSet<>(), new HashMap<>())",
                                    item.getSimpleName(),
                                    field.getName(),
                                    field.getRawType().getSimpleName())));
                      } else {
                        // Verify the collection is mutable by attempting a benign mutation.
                        assertMutable(item, field, value, events);
                      }
                    } catch (final NoSuchFieldException | IllegalAccessException e) {
                      events.add(
                          violated(
                              item,
                              String.format(
                                  "Record '%s': could not read field '%s': %s",
                                  item.getSimpleName(), field.getName(), e.getMessage())));
                    }
                  }
                }
              })
          .because(
              "MyBatis hydrates collection-mapped fields by calling mutating methods "
                  + "(e.g. .add(), .put()) on the existing instance; immutable defaults such as "
                  + "List.of() would cause UnsupportedOperationException at runtime");

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static boolean isCollectionField(final JavaField field) {
    try {
      final Class<?> fieldClass = Class.forName(field.getRawType().getName());
      return COLLECTION_TYPES.stream().anyMatch(ct -> ct.isAssignableFrom(fieldClass));
    } catch (final ClassNotFoundException e) {
      return false;
    }
  }

  /**
   * Reflectively creates an instance of the given record class, passing {@code null} for all
   * reference-type parameters and zero/false defaults for primitive parameters.
   */
  private static Object instantiateRecordWithNulls(final JavaClass archClass) throws Exception {
    final Class<?> recordClass = Class.forName(archClass.getName());
    final Constructor<?> canonicalCtor = recordClass.getDeclaredConstructors()[0];
    canonicalCtor.setAccessible(true);

    final Class<?>[] paramTypes = canonicalCtor.getParameterTypes();
    final Object[] args = new Object[paramTypes.length];
    for (int i = 0; i < paramTypes.length; i++) {
      args[i] = defaultValueFor(paramTypes[i]);
    }
    return canonicalCtor.newInstance(args);
  }

  private static Object defaultValueFor(final Class<?> type) {
    if (!type.isPrimitive()) {
      return null;
    }
    if (type == boolean.class) {
      return false;
    }
    if (type == byte.class) {
      return (byte) 0;
    }
    if (type == short.class) {
      return (short) 0;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    if (type == float.class) {
      return 0.0f;
    }
    if (type == double.class) {
      return 0.0;
    }
    if (type == char.class) {
      return '\0';
    }
    return null;
  }

  /**
   * Verifies that the given collection value is mutable by attempting a benign add/put operation.
   * Reports a violation if the collection throws {@link UnsupportedOperationException}.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void assertMutable(
      final JavaClass item,
      final JavaField field,
      final Object value,
      final ConditionEvents events) {
    try {
      if (value instanceof final Map map) {
        final Object key = new Object();
        map.put(key, new Object());
        map.remove(key);
      } else if (value instanceof final Collection collection) {
        final Object element = new Object();
        collection.add(element);
        collection.remove(element);
      }
    } catch (final UnsupportedOperationException e) {
      events.add(
          violated(
              item,
              String.format(
                  "Record '%s': collection field '%s' (type %s) defaults to an immutable "
                      + "collection — use a mutable implementation "
                      + "(e.g. new ArrayList<>(), new HashSet<>(), new HashMap<>())",
                  item.getSimpleName(), field.getName(), field.getRawType().getSimpleName())));
    }
  }
}
