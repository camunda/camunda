/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Walks the text values of a configuration object, such as a {@link Camunda}, and lets a visitor
 * replace them. Values are read from and written to fields, so getters that fall back to legacy
 * properties aren't involved.
 */
@NullMarked
public final class ConfigurationValues {

  private ConfigurationValues() {}

  /**
   * Visits every text value reachable from the given configuration object, including those in maps,
   * collections and optionals.
   */
  public static void walk(final Object configuration, final Visitor visitor) {
    new Walk(visitor).visitFields(configuration, "");
  }

  /** Visits one text value. */
  @FunctionalInterface
  public interface Visitor {

    /**
     * @param property the property that holds the value, relative to the walked object, such as
     *     {@code data.secondary-storage.elasticsearch.password}
     * @param field the field that holds the value, or the collection or map it is in; {@code null}
     *     for a value in a map or collection of objects that aren't configuration classes
     * @return the value to put in its place, or the value itself to keep it
     */
    String visit(String property, @Nullable Field field, String value);
  }

  private static final class Walk {

    private final Visitor visitor;
    private final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());

    private Walk(final Visitor visitor) {
      this.visitor = visitor;
    }

    /** Returns the replacement of the value, which is the value itself if nothing changed. */
    private @Nullable Object visit(
        final @Nullable Object value, final String property, final @Nullable Field field) {
      return switch (value) {
        case null -> null;
        case final String text -> visitor.visit(property, field, text);
        case final Optional<?> optional -> visitOptional(optional, property, field);
        case final Map<?, ?> map -> visitMap(map, property, field);
        case final List<?> list -> visitCollection(list, property, field, new ArrayList<>());
        case final Set<?> set -> visitCollection(set, property, field, new LinkedHashSet<>());
        case final Record record when isConfiguration(record) -> visitRecord(record, property);
        default -> {
          if (isConfiguration(value) && visited.add(value)) {
            visitFields(value, property);
          }
          yield value;
        }
      };
    }

    private Object visitOptional(
        final Optional<?> optional, final String property, final @Nullable Field field) {
      if (optional.isEmpty()) {
        return optional;
      }
      final var value = optional.get();
      final var replacement = visit(value, property, field);
      return replacement == value ? optional : Optional.ofNullable(replacement);
    }

    private Object visitMap(
        final Map<?, ?> map, final String property, final @Nullable Field field) {
      final Map<Object, @Nullable Object> replaced = new LinkedHashMap<>();
      var changed = false;
      for (final var entry : map.entrySet()) {
        final var value = entry.getValue();
        final var replacement =
            visit(value, child(property, String.valueOf(entry.getKey())), field);
        replaced.put(entry.getKey(), replacement);
        changed |= replacement != value;
      }
      return changed ? replaced : map;
    }

    private Object visitCollection(
        final Collection<?> collection,
        final String property,
        final @Nullable Field field,
        final Collection<@Nullable Object> replaced) {
      var changed = false;
      var index = 0;
      for (final var element : collection) {
        final var replacement = visit(element, property + "[" + index++ + "]", field);
        replaced.add(replacement);
        changed |= replacement != element;
      }
      return changed ? replaced : collection;
    }

    private Object visitRecord(final Record record, final String property) {
      final var components = record.getClass().getRecordComponents();
      final var values = new @Nullable Object[components.length];
      var changed = false;
      for (var i = 0; i < components.length; i++) {
        final var component = components[i];
        final var value = read(component, record);
        values[i] = visit(value, child(property, kebab(component.getName())), field(component));
        changed |= values[i] != value;
      }
      if (!changed) {
        return record;
      }
      try {
        final var types =
            Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        final var constructor = record.getClass().getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return constructor.newInstance(values);
      } catch (final ReflectiveOperationException e) {
        throw new IllegalStateException("Failed to replace a value of " + property, e);
      }
    }

    private void visitFields(final Object configuration, final String prefix) {
      for (Class<?> type = configuration.getClass();
          type != null && type != Object.class;
          type = type.getSuperclass()) {
        for (final var field : type.getDeclaredFields()) {
          if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
            continue;
          }
          final var property = child(prefix, kebab(field.getName()));
          try {
            field.setAccessible(true);
            final var value = field.get(configuration);
            final var replacement = visit(value, property, field);
            if (replacement != value) {
              field.set(configuration, replacement);
            }
          } catch (final IllegalAccessException e) {
            throw new IllegalStateException("Failed to replace the value of " + property, e);
          }
        }
      }
    }

    private static @Nullable Object read(final RecordComponent component, final Record record) {
      try {
        final var accessor = component.getAccessor();
        accessor.setAccessible(true);
        return accessor.invoke(record);
      } catch (final IllegalAccessException | InvocationTargetException e) {
        throw new IllegalStateException("Failed to read " + component, e);
      }
    }

    private static @Nullable Field field(final RecordComponent component) {
      try {
        return component.getDeclaringRecord().getDeclaredField(component.getName());
      } catch (final NoSuchFieldException e) {
        return null;
      }
    }

    private static boolean isConfiguration(final Object value) {
      final var type = value.getClass();
      return type.getName().startsWith("io.camunda.") && !type.isEnum();
    }

    private static String child(final String property, final String name) {
      return property.isEmpty() ? name : property + "." + name;
    }

    private static String kebab(final String name) {
      return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }
  }
}
