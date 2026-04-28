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
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * ArchUnit rules for records in {@code io.camunda.search.entities} and {@code
 * io.camunda.authentication.entity}.
 *
 * <p>These rules enforce:
 *
 * <ol>
 *   <li>All top-level types in {@code io.camunda.search.entities} (excluding enums and interfaces)
 *       must be Java {@code record}s.
 *   <li>Every record in {@code io.camunda.search.entities} with collection-type fields ({@link
 *       List}, {@link Set}, {@link Map}, {@link Collection}) must declare a compact constructor
 *       that defaults those fields to empty <b>mutable</b> instances (e.g. {@code new
 *       ArrayList<>()}). This is required because MyBatis hydrates collection-mapped fields by
 *       calling mutating methods on the existing instance.
 *   <li>Every record in {@code io.camunda.authentication.entity} with collection-type fields must
 *       declare a compact constructor that defaults those fields to empty instances (immutable
 *       defaults like {@code List.of()} are acceptable here).
 * </ol>
 */
@AnalyzeClasses(
    packages = {"io.camunda.search.entities", "io.camunda.authentication.entity"},
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

  /**
   * Every record in {@code io.camunda.authentication.entity} that declares collection-type fields
   * must have a compact constructor that defaults them to non-null empty instances when {@code
   * null} is passed. Unlike search entities, immutable defaults (e.g. {@code List.of()}) are
   * acceptable.
   */
  @ArchTest
  static final ArchRule AUTH_ENTITY_COLLECTION_FIELDS_MUST_HAVE_DEFAULTS =
      ArchRuleDefinition.classes()
          .that()
          .resideInAPackage("io.camunda.authentication.entity..")
          .and()
          .areRecords()
          .should(
              new ArchCondition<>(
                  "initialize collection-type fields with non-null defaults in a compact"
                      + " constructor") {
                @Override
                public void check(final JavaClass item, final ConditionEvents events) {
                  final var collectionFields =
                      item.getFields().stream()
                          .filter(SearchEntityArchTest::isCollectionField)
                          .toList();

                  if (collectionFields.isEmpty()) {
                    return;
                  }

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
                                        + "assigns an empty default "
                                        + "(e.g. List.of(), Map.of(), new ArrayList<>())",
                                    item.getSimpleName(),
                                    field.getName(),
                                    field.getRawType().getSimpleName())));
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
              "authentication entity records should default collection fields to non-null "
                  + "empty instances to avoid NullPointerExceptions");

  /**
   * Every record in {@code io.camunda.search.entities} that declares non-collection, non-primitive
   * components without {@link Nullable @Nullable} must guard them with a {@code
   * Objects.requireNonNull} check in its compact constructor. The package is {@code @NullMarked},
   * so the absence of {@code @Nullable} encodes a runtime non-null contract that we verify here by
   * passing {@code null} for the component and asserting the constructor throws {@link
   * NullPointerException}.
   */
  @ArchTest
  static final ArchRule NON_NULLABLE_FIELDS_MUST_HAVE_REQUIRE_NON_NULL_GUARD =
      ArchRuleDefinition.classes()
          .that()
          .resideInAPackage("io.camunda.search.entities..")
          .and()
          .areRecords()
          .should(
              new ArchCondition<>(
                  "guard every non-@Nullable reference component with Objects.requireNonNull"
                      + " in the compact constructor") {
                @Override
                public void check(final JavaClass item, final ConditionEvents events) {
                  final Class<?> recordClass;
                  final Constructor<?> canonicalCtor;
                  try {
                    recordClass = Class.forName(item.getName());
                    canonicalCtor = canonicalConstructor(recordClass);
                  } catch (final ReflectiveOperationException e) {
                    events.add(
                        violated(
                            item,
                            String.format(
                                "Record '%s': could not load canonical constructor: %s",
                                item.getSimpleName(), e.getMessage())));
                    return;
                  }

                  final Class<?>[] paramTypes = canonicalCtor.getParameterTypes();
                  final var components = recordClass.getRecordComponents();

                  for (int i = 0; i < paramTypes.length; i++) {
                    final Class<?> paramType = paramTypes[i];

                    // Primitive components cannot be null and need no guard.
                    if (paramType.isPrimitive()) {
                      continue;
                    }
                    // Collection-typed components are covered by the dedicated mutable-default
                    // rule above; passing null is part of their contract.
                    if (COLLECTION_TYPES.stream().anyMatch(ct -> ct.isAssignableFrom(paramType))) {
                      continue;
                    }
                    // Components explicitly opted out of null-safety are allowed to be null.
                    if (isNullable(components[i])) {
                      continue;
                    }

                    final Object[] args = new Object[paramTypes.length];
                    for (int j = 0; j < paramTypes.length; j++) {
                      args[j] = (j == i) ? null : defaultValueFor(paramTypes[j]);
                    }

                    try {
                      final Object instance = canonicalCtor.newInstance(args);
                      // Compact ctor may normalize null to a non-null default (e.g.
                      // `x = x != null ? x : DEFAULT;`). Reading the component back tells us
                      // whether the null was absorbed — an equivalent guarantee to requireNonNull.
                      final Object fieldValue = components[i].getAccessor().invoke(instance);
                      if (fieldValue == null) {
                        events.add(
                            violated(
                                item,
                                String.format(
                                    "Record '%s': non-@Nullable component '%s' (type %s) is not"
                                        + " guarded by Objects.requireNonNull in the compact"
                                        + " constructor — either add the guard, default the value"
                                        + " in the compact constructor, or annotate the component"
                                        + " with @org.jspecify.annotations.Nullable",
                                    item.getSimpleName(),
                                    components[i].getName(),
                                    paramType.getSimpleName())));
                      }
                    } catch (final InvocationTargetException e) {
                      // Expected: Objects.requireNonNull(x, "x") throws NPE with message equal to
                      // the component name. We assert the message matches, otherwise the NPE may
                      // have fired on a different non-null component whose defaultValueFor(...)
                      // returned null (e.g. Unsafe allocation failure) — a false positive that
                      // would silently credit the current component as guarded.
                      if (!(e.getCause() instanceof NullPointerException)) {
                        events.add(
                            violated(
                                item,
                                String.format(
                                    "Record '%s': constructing with null for component '%s' threw"
                                        + " unexpected %s — expected NullPointerException from a"
                                        + " requireNonNull guard",
                                    item.getSimpleName(),
                                    components[i].getName(),
                                    e.getCause().getClass().getName())));
                      } else {
                        final String expected = components[i].getName();
                        final String actual = e.getCause().getMessage();
                        if (!expected.equals(actual)) {
                          events.add(
                              violated(
                                  item,
                                  String.format(
                                      "Record '%s': expected NullPointerException message '%s'"
                                          + " (matching component name) from a requireNonNull"
                                          + " guard on component '%s', but got '%s' — the NPE may"
                                          + " have fired on a different component",
                                      item.getSimpleName(), expected, expected, actual)));
                        }
                      }
                    } catch (final ReflectiveOperationException e) {
                      events.add(
                          violated(
                              item,
                              String.format(
                                  "Record '%s': could not invoke canonical constructor while"
                                      + " verifying null-guard for component '%s': %s",
                                  item.getSimpleName(), components[i].getName(), e.getMessage())));
                    }
                  }
                }
              })
          .because(
              "io.camunda.search.entities is @NullMarked; non-@Nullable record components carry"
                  + " a non-null contract that must be enforced at construction time so that"
                  + " violations from MyBatis hydration or transformer bugs fail fast at the"
                  + " entity boundary instead of leaking nulls into the API layer");

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  // sun.misc.Unsafe access for allocating instances without invoking constructors. Used only by
  // this ArchUnit test to satisfy non-null parameter requirements when reflectively instantiating
  // records to verify their collection-field defaulting logic. sun.misc.Unsafe lives in the
  // jdk.unsupported module, which is open for reflection without --add-opens on current JDKs, so
  // the setAccessible call succeeds; the catch is broadened to Exception to also cover any future
  // runtime access restriction (e.g., if module rules tighten) and to degrade gracefully if the
  // class is ever removed (JEP 471 has deprecated most of Unsafe for removal).
  private static final Object UNSAFE;
  private static final java.lang.reflect.Method UNSAFE_ALLOCATE;

  static {
    Object unsafe;
    java.lang.reflect.Method allocate;
    try {
      final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
      final java.lang.reflect.Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
      theUnsafe.setAccessible(true);
      unsafe = theUnsafe.get(null);
      allocate = unsafeClass.getMethod("allocateInstance", Class.class);
    } catch (final Exception e) {
      unsafe = null;
      allocate = null;
    }
    UNSAFE = unsafe;
    UNSAFE_ALLOCATE = allocate;
  }

  /**
   * Returns {@code true} if the given record component is annotated with {@link Nullable @Nullable}
   * (jspecify) — either as a TYPE_USE annotation on its declared type or directly on the record
   * component itself.
   */
  private static boolean isNullable(final java.lang.reflect.RecordComponent component) {
    return component.isAnnotationPresent(Nullable.class)
        || component.getAnnotatedType().isAnnotationPresent(Nullable.class);
  }

  /**
   * Returns the canonical constructor of the given record class — i.e. the constructor whose
   * parameter types match the record's declared components in order. Records may declare additional
   * convenience constructors, so {@link Class#getDeclaredConstructors()} alone is not
   * deterministic.
   */
  private static Constructor<?> canonicalConstructor(final Class<?> recordClass)
      throws NoSuchMethodException {
    final var components = recordClass.getRecordComponents();
    final Class<?>[] componentTypes = new Class<?>[components.length];
    for (int i = 0; i < components.length; i++) {
      componentTypes[i] = components[i].getType();
    }
    final Constructor<?> ctor = recordClass.getDeclaredConstructor(componentTypes);
    ctor.setAccessible(true);
    return ctor;
  }

  private static boolean isCollectionField(final JavaField field) {
    try {
      final Class<?> fieldClass = Class.forName(field.getRawType().getName());
      return COLLECTION_TYPES.stream().anyMatch(ct -> ct.isAssignableFrom(fieldClass));
    } catch (final ClassNotFoundException e) {
      return false;
    }
  }

  /**
   * Reflectively creates an instance of the given record class. Passes {@code null} for all
   * collection-typed parameters (so the compact constructor's defaulting logic is exercised), and
   * non-null sentinel values for every other reference parameter (so compact-constructor null
   * guards on required fields don't interfere with this test).
   */
  private static Object instantiateRecordWithNulls(final JavaClass archClass) throws Exception {
    final Class<?> recordClass = Class.forName(archClass.getName());
    final Constructor<?> canonicalCtor = canonicalConstructor(recordClass);

    final Class<?>[] paramTypes = canonicalCtor.getParameterTypes();
    final Object[] args = new Object[paramTypes.length];
    for (int i = 0; i < paramTypes.length; i++) {
      args[i] = defaultValueFor(paramTypes[i]);
    }
    return canonicalCtor.newInstance(args);
  }

  private static Object defaultValueFor(final Class<?> type) {
    if (type.isPrimitive()) {
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
    // Collection-typed parameters must be passed as null so the compact ctor's defaulting logic
    // is exercised and verified.
    if (COLLECTION_TYPES.stream().anyMatch(ct -> ct.isAssignableFrom(type))) {
      return null;
    }
    if (type == String.class) {
      return "";
    }
    if (type.isEnum()) {
      final Object[] constants = type.getEnumConstants();
      return constants != null && constants.length > 0 ? constants[0] : null;
    }
    if (type.isArray()) {
      return java.lang.reflect.Array.newInstance(type.getComponentType(), 0);
    }
    // For any other reference type, allocate a zero-initialized instance without invoking its
    // constructor. This produces a non-null sentinel that satisfies Objects.requireNonNull guards
    // in the record's compact constructor without us needing to know how to construct that type.
    // Guard against a missing Unsafe (static init failed, e.g. future JDK removed the class) so
    // the test degrades to null rather than NPE-ing the whole suite.
    if (UNSAFE_ALLOCATE == null) {
      return null;
    }
    try {
      return UNSAFE_ALLOCATE.invoke(UNSAFE, type);
    } catch (final Exception e) {
      return null;
    }
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
