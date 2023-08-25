/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.ContainsForeignKeys;
import io.camunda.zeebe.db.DbKey;
import java.util.Collection;
import java.util.Collections;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * The DbTenantAwareKey wraps any given key and appends it with the tenantKey.
 *
 * <p>We have chosen to append the tenant at the end of the wrapped key because:
 *
 * <ul>
 *   <li>The tenant id must be part of the key to ensure uniqueness (e.g. when storing a process id
 *       and version we need to include the tenant in the key, otherwise they will start overwriting
 *       eachother)
 *   <li>It provides flexibility when doing lookups. We can search for prefixes to ignore the
 *       tenant. We can search by suffix to find everything belonging to a single tenant.
 *   <li>We won't have preferential treatment. Keys in RocksDb are sorted. If we take Jobs as an
 *       example, when activating we iterate over all activateable jobs to return to the worker. We
 *       don't want to Jobs of tenant AAA to take priority over Jobs of tenant ZZZ.
 * </ul>
 */
public record DbTenantAwareKey<WrappedKey extends DbKey>(DbString tenantKey, WrappedKey wrappedKey)
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
    wrappedKey.wrap(buffer, offset, length);
    final var wrappedKeyLength = wrappedKey.getLength();
    tenantKey.wrap(buffer, offset + wrappedKeyLength, length - wrappedKeyLength);
  }

  @Override
  public int getLength() {
    return wrappedKey.getLength() + tenantKey.getLength();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    wrappedKey.write(buffer, offset);
    final var wrappedKeyLength = wrappedKey.getLength();
    tenantKey.write(buffer, offset + wrappedKeyLength);
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
}
