/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import static dev.hegel.Generators.lists;
import static dev.hegel.Generators.maps;
import static dev.hegel.Generators.optional;
import static dev.hegel.Generators.sets;

import dev.hegel.Generator;
import dev.hegel.Generators;
import dev.hegel.generators.RecordGenerator;
import io.camunda.zeebe.util.ReflectUtil;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Derives generators for the configuration state model by reflection, on top of what {@link
 * Generators#forType} supports: sealed interfaces generate any of their (transitively) permitted
 * implementations, sorted collections are generated as such, and record components whose type has a
 * registered generator use that one. Deriving from the types keeps a newly added implementation of
 * a sealed interface covered without touching the generators.
 */
final class DerivedGenerators {

  private final Map<Class<?>, Generator<?>> registered;

  DerivedGenerators(final Map<Class<?>, Generator<?>> registered) {
    this.registered = Map.copyOf(registered);
  }

  @SuppressWarnings("unchecked")
  <T> Generator<T> forType(final Class<T> type) {
    return (Generator<T>) derive(type);
  }

  private Generator<?> derive(final Type type) {
    if (type instanceof final ParameterizedType parameterized) {
      return deriveParameterized(parameterized);
    }
    if (type instanceof final Class<?> cls) {
      return deriveClass(cls);
    }
    throw new IllegalArgumentException("Cannot derive a generator for type " + type);
  }

  private Generator<?> deriveClass(final Class<?> cls) {
    final var generator = registered.get(cls);
    if (generator != null) {
      return generator;
    }
    if (cls.isSealed()) {
      final var implementations =
          ReflectUtil.implementationsOfSealedInterface(cls).map(this::deriveClass).toList();
      return Generators.oneOf(implementations.toArray(new Generator<?>[0]));
    }
    if (cls.isRecord()) {
      RecordGenerator<?> records = Generators.records(cls);
      for (final var component : cls.getRecordComponents()) {
        records = records.with(component.getName(), derive(component.getGenericType()));
      }
      return records;
    }
    return Generators.forType(cls);
  }

  @SuppressWarnings("unchecked")
  private Generator<?> deriveParameterized(final ParameterizedType type) {
    final var raw = (Class<?>) type.getRawType();
    final var arguments = type.getActualTypeArguments();
    final var first = (Generator<Object>) derive(arguments[0]);
    if (raw == SortedMap.class) {
      return maps(first, (Generator<Object>) derive(arguments[1])).map(TreeMap::new);
    }
    if (raw == SortedSet.class) {
      return sets(first).map(TreeSet::new);
    }
    if (raw == List.class) {
      return lists(first);
    }
    if (raw == Set.class) {
      return sets(first);
    }
    if (raw == Map.class) {
      return maps(first, (Generator<Object>) derive(arguments[1]));
    }
    if (raw == Optional.class) {
      return optional(first);
    }
    throw new IllegalArgumentException("Cannot derive a generator for generic type " + type);
  }
}
