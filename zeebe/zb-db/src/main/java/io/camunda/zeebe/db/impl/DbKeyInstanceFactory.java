/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.db.impl.rocksdb.DbNullKey;
import java.util.function.Predicate;

/** Utility to create blank instances of known {@link DbKey} types, including wrapper keys. */
public final class DbKeyInstanceFactory {

  private DbKeyInstanceFactory() {}

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <T extends DbKey> T newInstance(final T template) {
    return switch (template) {
      case final DbLong ignored -> (T) new DbLong();
      case final DbInt ignored -> (T) new DbInt();
      case final DbShort ignored -> (T) new DbShort();
      case final DbByte ignored -> (T) new DbByte();
      case final DbString ignored -> (T) new DbString();
      case final DbBytes ignored -> (T) new DbBytes();
      case final DbNullKey ignored -> (T) DbNullKey.INSTANCE;
      case final DbCompositeKey<?, ?> key ->
          (T) new DbCompositeKey<>(newInstance(key.first()), newInstance(key.second()));
      case final DbForeignKey<?> key ->
          (T)
              new DbForeignKey(
                  newInstance((DbKey) key.inner()),
                  key.columnFamily(),
                  key.match(),
                  (Predicate) key.skip());
      case final DbTenantAwareKey<?> key ->
          (T)
              new DbTenantAwareKey(
                  (DbString) newInstance(key.tenantKey()),
                  newInstance((DbKey) key.wrappedKey()),
                  (PlacementType) key.placementType());
      default -> {
        try {
          final var ctor = template.getClass().getDeclaredConstructor();
          ctor.setAccessible(true);
          yield (T) ctor.newInstance();
        } catch (final NoSuchMethodException e) {
          throw new UnsupportedOperationException(
              template.getClass().getName()
                  + " has no supported constructor path for in-memory storage.",
              e);
        } catch (final Exception e) {
          throw new RuntimeException(
              "Failed to create new key instance of " + template.getClass().getName(), e);
        }
      }
    };
  }
}
