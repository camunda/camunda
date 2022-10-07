/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.engine.api.StreamProcessorLifecycleAware;
import io.prometheus.client.Gauge;
import java.io.File;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.rocksdb.TickerType;

public class ZbDBStatsDecorator
    implements ZeebeDb<ZbColumnFamilies>, StreamProcessorLifecycleAware {
  private static final String NAMESPACE = "zeebe";
  private static final String PARTITION_LABEL = "partition";
  static final Gauge STATE_COUNTER =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("state_counter_total")
          .help("Current counter for column family ")
          .labelNames(PARTITION_LABEL, "columnFamily")
          .register();

  private final ZeebeDb<ZbColumnFamilies> zeebeDb;

  private final ColumnFamily<DbString, NextValue> cfCountColumnFamily;
  private final DbString cfKey;
  private final NextValue nextValue = new NextValue();
  private boolean metricsEnabled;
  private final String partitionId;

  public ZbDBStatsDecorator(final ZeebeDb<ZbColumnFamilies> zeebeDb, final String parittionId) {
    this.zeebeDb = zeebeDb;
    partitionId = parittionId;

    cfKey = new DbString();
    cfCountColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEFAULT, zeebeDb.createContext(), cfKey, nextValue);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    metricsEnabled = true;
    cfCountColumnFamily.forEach(
        (type, value) -> STATE_COUNTER.labels(partitionId, type.toString()).set(value.get()));
  }

  public void increment(final String key) {
    final long currentValue = getCurrentValue(key);
    final long next = currentValue + 1;
    nextValue.set(next);
    cfCountColumnFamily.upsert(cfKey, nextValue);

    if (metricsEnabled) {
      STATE_COUNTER.labels(partitionId, key).inc();
    }
  }

  public void decrement(final String key) {
    final long currentValue = getCurrentValue(key);
    final long next = currentValue - 1;
    nextValue.set(next);
    cfCountColumnFamily.upsert(cfKey, nextValue);

    if (metricsEnabled) {
      STATE_COUNTER.labels(partitionId, key).dec();
    }
  }

  private long getCurrentValue(final String key) {
    cfKey.wrapString(key);
    return getCurrentValue();
  }

  private long getCurrentValue() {
    final NextValue readValue = cfCountColumnFamily.get(cfKey);

    final long initialValue = 0;
    long currentValue = initialValue;
    if (readValue != null) {
      currentValue = readValue.get();
    }
    return currentValue;
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ZbColumnFamilies columnFamily,
          final TransactionContext context,
          final KeyType keyInstance,
          final ValueType valueInstance) {

    return new CFDecorator<>(
        zeebeDb.createColumnFamily(columnFamily, context, keyInstance, valueInstance),
        columnFamily.name(),
        this::increment,
        this::decrement);
  }

  @Override
  public void createSnapshot(final File snapshotDir) {
    zeebeDb.createSnapshot(snapshotDir);
  }

  @Override
  public Optional<String> getProperty(final String propertyName) {
    return zeebeDb.getProperty(propertyName);
  }

  @Override
  public Optional<Long> getStatistics(final TickerType type) {
    return zeebeDb.getStatistics(type);
  }

  @Override
  public TransactionContext createContext() {
    return zeebeDb.createContext();
  }

  @Override
  public boolean isEmpty(final ZbColumnFamilies column, final TransactionContext context) {
    return zeebeDb.isEmpty(column, context);
  }

  @Override
  public void close() throws Exception {
    zeebeDb.close();
  }

  private static final class CFDecorator<KeyType extends DbKey, ValueType extends DbValue>
      implements ColumnFamily<KeyType, ValueType> {

    private final ColumnFamily<KeyType, ValueType> cf;
    private final String cfName;
    private final Consumer<String> inc;
    private final Consumer<String> dec;

    private CFDecorator(
        final ColumnFamily<KeyType, ValueType> cf,
        final String cfName,
        final Consumer<String> inc,
        final Consumer<String> dec) {
      this.cf = cf;
      this.cfName = cfName;
      this.inc = inc;
      this.dec = dec;
    }

    @Override
    public void insert(final KeyType key, final ValueType value) {
      cf.insert(key, value);
      inc.accept(cfName);
    }

    @Override
    public void update(final KeyType key, final ValueType value) {
      cf.update(key, value);
    }

    @Override
    public void upsert(final KeyType key, final ValueType value) {
      if (cf.get(key) == null) {
        inc.accept(cfName);
      }
      cf.upsert(key, value);
    }

    @Override
    public ValueType get(final KeyType key) {
      return cf.get(key);
    }

    @Override
    public void forEach(final Consumer<ValueType> consumer) {
      cf.forEach(consumer);
    }

    @Override
    public void forEach(final BiConsumer<KeyType, ValueType> consumer) {
      cf.forEach(consumer);
    }

    @Override
    public void whileTrue(final KeyValuePairVisitor<KeyType, ValueType> visitor) {
      cf.whileTrue(visitor);
    }

    @Override
    public void whileEqualPrefix(
        final DbKey keyPrefix, final BiConsumer<KeyType, ValueType> visitor) {
      cf.whileEqualPrefix(keyPrefix, visitor);
    }

    @Override
    public void whileEqualPrefix(
        final DbKey keyPrefix, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
      cf.whileEqualPrefix(keyPrefix, visitor);
    }

    @Override
    public void deleteExisting(final KeyType key) {
      cf.deleteExisting(key);
      dec.accept(cfName);
    }

    @Override
    public void deleteIfExists(final KeyType key) {
      if (cf.get(key) != null) {
        dec.accept(cfName);
      }
      cf.deleteIfExists(key);
    }

    @Override
    public boolean exists(final KeyType key) {
      return cf.exists(key);
    }

    @Override
    public boolean isEmpty() {
      return cf.isEmpty();
    }
  }
}
