/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.impl.DbKeyInstanceFactory;
import io.camunda.zeebe.db.impl.DbNil;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Creates blank instances of a value's class, so that the store can hold values in objects that no
 * caller has a reference to.
 *
 * <p>{@link DbNil} has no state and is shared. Values that are also keys, like {@link
 * io.camunda.zeebe.db.impl.DbLong} or composite keys, need the parts of the template, so {@link
 * DbKeyInstanceFactory} builds them. Every other value class needs a no-arg constructor, of any
 * visibility; it is looked up once per class.
 */
final class ValueInstances {

  private static final MethodType CONSTRUCTOR_TYPE = MethodType.methodType(DbValue.class);

  private static final ClassValue<MethodHandle> CONSTRUCTORS =
      new ClassValue<>() {
        @Override
        protected MethodHandle computeValue(final Class<?> type) {
          try {
            final var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return MethodHandles.lookup()
                .unreflectConstructor(constructor)
                .asType(CONSTRUCTOR_TYPE);
          } catch (final NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalArgumentException(
                "Expected value class %s to have a no-arg constructor, but it has none that can be used"
                    .formatted(type.getName()),
                e);
          }
        }
      };

  private ValueInstances() {}

  /** A new, blank instance of the template's class, built like the template if it is a key. */
  static DbValue newInstance(final DbValue template) {
    if (template instanceof DbNil) {
      return DbNil.INSTANCE;
    }
    if (template instanceof final DbKey key) {
      return (DbValue) DbKeyInstanceFactory.newInstance(key);
    }
    try {
      return (DbValue) CONSTRUCTORS.get(template.getClass()).invokeExact();
    } catch (final RuntimeException | Error e) {
      throw e;
    } catch (final Throwable e) {
      throw new IllegalStateException(
          "Failed to create an instance of value class " + template.getClass().getName(), e);
    }
  }
}
