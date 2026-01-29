/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.ContainsForeignKeys;
import io.camunda.zeebe.db.DbKey;
import java.util.Collection;
import java.util.Collections;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * The DbTenantAwareKey wraps any given key and, depending on the PlacementType, append or prepends
 * the tenant to it.
 *
 * <p>Depending on the use case you want to choose whether you need to use PREFIX or SUFFIX. PREFIX
 * is suitable when you want the ability to search values by tenant. Use SUFFIX when you want to
 * search for values irrelevant of tenant.
 *
 * <p>When using PREFIX it's important to be aware that we could have preferential treatment. Keys
 * in RocksDb are sorted. If we take Jobs as an example, when activating we iterate over all
 * activatable jobs to return to the worker. We don't want to Jobs of tenant AAA to take priority
 * over Jobs of tenant ZZZ. SUFFIX is more suitable for this case.
 */
public record DbTenantAwareKey<WrappedKey extends DbKey>(
    DbString tenantKey, WrappedKey wrappedKey, PlacementType placementType)
    implements DbKey, ContainsForeignKeys {

  @Override
  public DbString tenantKey() {
    return tenantKey;
  }

  @Override
  public WrappedKey wrappedKey() {
    return wrappedKey;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    switch (placementType) {
      case PREFIX -> {
        tenantKey.wrap(buffer, offset, length);
        final var tenantKeyLength = tenantKey.getLength();
        wrappedKey.wrap(buffer, offset + tenantKeyLength, length - tenantKeyLength);
      }
      case SUFFIX -> {
        wrappedKey.wrap(buffer, offset, length);
        final var wrappedKeyLength = wrappedKey.getLength();
        tenantKey.wrap(buffer, offset + wrappedKeyLength, length - wrappedKeyLength);
      }
      default -> throw new IllegalStateException("Unexpected value: " + placementType);
    }
  }

  @Override
  public int getLength() {
    return wrappedKey.getLength() + tenantKey.getLength();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    switch (placementType) {
      case PREFIX -> {
        int written = tenantKey.write(buffer, offset);
        written += wrappedKey.write(buffer, offset + written);
        return written;
      }
      case SUFFIX -> {
        int written = wrappedKey.write(buffer, offset);
        written += tenantKey.write(buffer, offset + written);
        return written;
      }
      default -> throw new IllegalStateException("Unexpected value: " + placementType);
    }
  }

  // TODO: To consider: maybe we should introduce a DbTenantAwareForeignKey class instead of saying
  //  this always contains foreign keys.
  @Override
  public Collection<DbForeignKey<DbKey>> containedForeignKeys() {
    if (wrappedKey instanceof ContainsForeignKeys) {
      return ((ContainsForeignKeys) wrappedKey).containedForeignKeys();
    }

    return Collections.emptyList();
  }

  public enum PlacementType {
    PREFIX,
    SUFFIX
  }
}
