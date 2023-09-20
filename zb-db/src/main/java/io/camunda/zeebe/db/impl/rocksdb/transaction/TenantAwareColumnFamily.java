/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.MultiTenancySettings;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class TenantAwareColumnFamily<
        ColumnFamilyNames extends Enum<ColumnFamilyNames>,
        KeyType extends DbKey,
        ValueType extends DbValue>
    implements ColumnFamily<KeyType, ValueType> {

  private final DbString defaultTenant = new DbString();
  private final DbString tenantKey;
  private final TransactionalColumnFamily<ColumnFamilyNames, KeyType, ValueType>
      defaultTenantColumnFamily;
  private final TransactionalColumnFamily<ColumnFamilyNames, KeyType, ValueType>
      tenantSpecificColumnFamily;

  public TenantAwareColumnFamily(
      final ZeebeTransactionDb<ColumnFamilyNames> transactionDb,
      final ConsistencyChecksSettings consistencyChecksSettings,
      final MultiTenancySettings multiTenancySettings,
      final ColumnFamilyNames columnFamily,
      final TransactionContext context,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final DbString tenantKey) {

    this.defaultTenantColumnFamily =
        new TransactionalColumnFamily<>(
            transactionDb,
            consistencyChecksSettings,
            columnFamily,
            context,
            new DefaultColumnFamilyKey(columnFamily.ordinal()),
            keyInstance,
            valueInstance,
            new ForeignKeyChecker(
                transactionDb,
                consistencyChecksSettings,
                (e) -> new DefaultColumnFamilyKey(e.ordinal())));

    this.tenantSpecificColumnFamily =
        new TransactionalColumnFamily<>(
            transactionDb,
            consistencyChecksSettings,
            columnFamily,
            context,
            new TenantAwareColumnFamilyKey(columnFamily.ordinal(), tenantKey),
            keyInstance,
            valueInstance,
            new ForeignKeyChecker(
                transactionDb,
                consistencyChecksSettings,
                (e) -> new TenantAwareColumnFamilyKey(e.ordinal(), tenantKey)));

    defaultTenant.wrapString(multiTenancySettings.defaultTenant());
    this.tenantKey = Objects.requireNonNull(tenantKey);
  }

  @Override
  public void insert(KeyType key, ValueType value) {
    if (isDefaultTenant()) {
      defaultTenantColumnFamily.insert(key, value);
    } else {
      tenantSpecificColumnFamily.insert(key, value);
    }
  }

  @Override
  public void update(KeyType key, ValueType value) {
    if (isDefaultTenant()) {
      defaultTenantColumnFamily.update(key, value);
    } else {
      tenantSpecificColumnFamily.update(key, value);
    }
  }

  @Override
  public void upsert(KeyType key, ValueType value) {
    if (isDefaultTenant()) {
      defaultTenantColumnFamily.upsert(key, value);
    } else {
      tenantSpecificColumnFamily.upsert(key, value);
    }
  }

  @Override
  public ValueType get(KeyType key) {
    if (isDefaultTenant()) {
      return defaultTenantColumnFamily.get(key);
    } else {
      return tenantSpecificColumnFamily.get(key);
    }
  }

  @Override
  public void forEach(Consumer<ValueType> consumer) {
    if (isDefaultTenant()) {
      defaultTenantColumnFamily.forEach(consumer);
    } else {
      tenantSpecificColumnFamily.forEach(consumer);
    }
  }

  @Override
  public void forEach(BiConsumer<KeyType, ValueType> consumer) {
    if (isDefaultTenant()) {
      defaultTenantColumnFamily.forEach(consumer);
    } else {
      tenantSpecificColumnFamily.forEach(consumer);
    }
  }

  @Override
  public void whileTrue(KeyType startAtKey, KeyValuePairVisitor<KeyType, ValueType> visitor) {
    if (isDefaultTenant()) {
      defaultTenantColumnFamily.whileTrue(startAtKey, visitor);
    } else {
      tenantSpecificColumnFamily.whileTrue(startAtKey, visitor);
    }
  }

  @Override
  public void whileTrue(KeyValuePairVisitor<KeyType, ValueType> visitor) {
    if (isDefaultTenant()) {
      defaultTenantColumnFamily.whileTrue(visitor);
    } else {
      tenantSpecificColumnFamily.whileTrue(visitor);
    }
  }

  @Override
  public void whileEqualPrefix(DbKey keyPrefix, BiConsumer<KeyType, ValueType> visitor) {
    if (isDefaultTenant()) {
      defaultTenantColumnFamily.whileEqualPrefix(keyPrefix, visitor);
    } else {
      tenantSpecificColumnFamily.whileEqualPrefix(keyPrefix, visitor);
    }
  }

  @Override
  public void whileEqualPrefix(DbKey keyPrefix, KeyValuePairVisitor<KeyType, ValueType> visitor) {
    if (isDefaultTenant()) {
      defaultTenantColumnFamily.whileEqualPrefix(keyPrefix, visitor);
    } else {
      tenantSpecificColumnFamily.whileEqualPrefix(keyPrefix, visitor);
    }
  }

  @Override
  public void whileEqualPrefix(
      DbKey keyPrefix, KeyType startAtKey, KeyValuePairVisitor<KeyType, ValueType> visitor) {
    if (isDefaultTenant()) {
      defaultTenantColumnFamily.whileEqualPrefix(keyPrefix, visitor);
    } else {
      tenantSpecificColumnFamily.whileEqualPrefix(keyPrefix, visitor);
    }
  }

  @Override
  public void deleteExisting(KeyType key) {
    if (isDefaultTenant()) {
      defaultTenantColumnFamily.deleteExisting(key);
    } else {
      tenantSpecificColumnFamily.deleteExisting(key);
    }
  }

  @Override
  public void deleteIfExists(KeyType key) {
    if (isDefaultTenant()) {
      defaultTenantColumnFamily.deleteIfExists(key);
    } else {
      tenantSpecificColumnFamily.deleteIfExists(key);
    }
  }

  @Override
  public boolean exists(KeyType key) {
    if (isDefaultTenant()) {
      return defaultTenantColumnFamily.exists(key);
    } else {
      return tenantSpecificColumnFamily.exists(key);
    }
  }

  @Override
  public boolean isEmpty() {
    if (isDefaultTenant()) {
      return defaultTenantColumnFamily.isEmpty();
    } else {
      return tenantSpecificColumnFamily.isEmpty();
    }
  }

  private boolean isDefaultTenant() {
    return BufferUtil.equals(defaultTenant.getBuffer(), tenantKey.getBuffer());
  }
}
