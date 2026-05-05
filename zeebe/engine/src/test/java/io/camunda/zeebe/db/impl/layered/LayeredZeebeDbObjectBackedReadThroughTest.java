/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.layered;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LayeredZeebeDbObjectBackedReadThroughTest {

  private static final ConsistencyChecksSettings CONSISTENCY_CHECKS =
      new ConsistencyChecksSettings(true, true);
  private static final AccessMetricsConfiguration ACCESS_METRICS_CONFIGURATION =
      new AccessMetricsConfiguration(Kind.NONE, 1);

  private final ZeebeDbFactory<DefaultColumnFamily> persistentFactory =
      new ZeebeRocksDbFactory<>(
          new RocksDbConfiguration(),
          CONSISTENCY_CHECKS,
          ACCESS_METRICS_CONFIGURATION,
          SimpleMeterRegistry::new);

  @Test
  void shouldPreserveUndeclaredPropertiesOnPlainReadThroughGets(final @TempDir File path)
      throws Exception {
    // given
    writePersistentKnownOnlyValue(
        path, 1L, wrapKnownOnlyValueWithExtraProperty("known-1", "extra-1"));

    try (final var layeredDb = openLayeredDb(path)) {
      final var columnFamily = knownOnlyColumnFamily(layeredDb);
      final var key = new DbLong();
      key.wrapLong(1L);

      // when
      final var firstRawResult = columnFamily.get(key);
      deletePersistentKnownOnlyValue(layeredDb, 1L);
      final var secondRawResult = columnFamily.get(key);

      // then
      assertThat(firstRawResult).isNotNull();
      assertThat(secondRawResult).isNotNull();

      final var firstResult = decode(firstRawResult);
      final var secondResult = decode(secondRawResult);
      assertThat(firstResult.getKnown()).isEqualTo("known-1");
      assertThat(firstResult.getExtra()).isEqualTo("extra-1");
      assertThat(secondResult.getKnown()).isEqualTo("known-1");
      assertThat(secondResult.getExtra()).isEqualTo("extra-1");
    }
  }

  @Test
  void shouldPreserveUndeclaredPropertiesOnSupplierReadThroughGets(final @TempDir File path)
      throws Exception {
    // given
    writePersistentKnownOnlyValue(
        path, 1L, wrapKnownOnlyValueWithExtraProperty("known-1", "extra-1"));

    try (final var layeredDb = openLayeredDb(path)) {
      final var columnFamily = knownOnlyColumnFamily(layeredDb);
      final var key = new DbLong();
      key.wrapLong(1L);

      // when
      final var firstRawResult = columnFamily.get(key, KnownOnlyValue::new);
      deletePersistentKnownOnlyValue(layeredDb, 1L);
      final var secondRawResult = columnFamily.get(key, KnownOnlyValue::new);

      // then
      assertThat(firstRawResult).isNotNull();
      assertThat(secondRawResult).isNotNull();

      final var firstResult = decode(firstRawResult);
      final var secondResult = decode(secondRawResult);
      assertThat(firstResult.getKnown()).isEqualTo("known-1");
      assertThat(firstResult.getExtra()).isEqualTo("extra-1");
      assertThat(secondResult.getKnown()).isEqualTo("known-1");
      assertThat(secondResult.getExtra()).isEqualTo("extra-1");
    }
  }

  @SuppressWarnings("unchecked")
  private LayeredZeebeDb<DefaultColumnFamily> openLayeredDb(final File path) {
    return (LayeredZeebeDb<DefaultColumnFamily>)
        new LayeredZeebeDbFactory<DefaultColumnFamily>(
                new RocksDbConfiguration(),
                CONSISTENCY_CHECKS,
                ACCESS_METRICS_CONFIGURATION,
                SimpleMeterRegistry::new)
            .createDb(path);
  }

  private void writePersistentKnownOnlyValue(
      final File path, final long keyValue, final KnownOnlyValue value) throws Exception {
    try (final var persistentDb = persistentFactory.createDb(path, false)) {
      final var columnFamily = knownOnlyColumnFamily(persistentDb);
      final var key = new DbLong();
      key.wrapLong(keyValue);
      columnFamily.insert(key, value);
    }
  }

  private void deletePersistentKnownOnlyValue(
      final LayeredZeebeDb<DefaultColumnFamily> layeredDb, final long keyValue) {
    final var columnFamily = knownOnlyColumnFamily(layeredDb.persistentDb());
    final var key = new DbLong();
    key.wrapLong(keyValue);
    columnFamily.deleteExisting(key);
  }

  private ColumnFamily<DbLong, KnownOnlyValue> knownOnlyColumnFamily(
      final ZeebeDb<DefaultColumnFamily> db) {
    return db.createColumnFamily(
        DefaultColumnFamily.DEFAULT, db.createContext(), new DbLong(), new KnownOnlyValue());
  }

  private KnownOnlyValue wrapKnownOnlyValueWithExtraProperty(
      final String knownValue, final String extraValue) {
    final var source = new KnownAndExtraValue().setKnown(knownValue).setExtra(extraValue);
    final var wrapped = new KnownOnlyValue();
    final var buffer = serialize(source);
    wrapped.wrap(buffer, 0, buffer.capacity());
    return wrapped;
  }

  private KnownAndExtraValue decode(final DbValue value) {
    final var decoded = new KnownAndExtraValue();
    final var buffer = serialize(value);
    decoded.wrap(buffer, 0, buffer.capacity());
    return decoded;
  }

  private UnsafeBuffer serialize(final DbValue value) {
    final byte[] bytes = new byte[value.getLength()];
    final var buffer = new UnsafeBuffer(bytes);
    value.write(buffer, 0);
    return buffer;
  }

  private static final class KnownOnlyValue extends UnpackedObject implements DbValue {
    private final StringProperty knownProp = new StringProperty("known", "");

    private KnownOnlyValue() {
      super(1);
      declareProperty(knownProp);
    }

    @Override
    public void copyTo(final DbValue target) {
      super.copyTo((KnownOnlyValue) target);
    }

    @Override
    public KnownOnlyValue newInstance() {
      return new KnownOnlyValue();
    }
  }

  private static final class KnownAndExtraValue extends UnpackedObject implements DbValue {
    private final StringProperty knownProp = new StringProperty("known", "");
    private final StringProperty extraProp = new StringProperty("extra", "");

    private KnownAndExtraValue() {
      super(2);
      declareProperty(knownProp).declareProperty(extraProp);
    }

    private KnownAndExtraValue setKnown(final String known) {
      knownProp.setValue(known);
      return this;
    }

    private KnownAndExtraValue setExtra(final String extra) {
      extraProp.setValue(extra);
      return this;
    }

    private String getKnown() {
      return BufferUtil.bufferAsString(knownProp.getValue());
    }

    private String getExtra() {
      return BufferUtil.bufferAsString(extraProp.getValue());
    }

    @Override
    public void copyTo(final DbValue target) {
      super.copyTo((KnownAndExtraValue) target);
    }

    @Override
    public KnownAndExtraValue newInstance() {
      return new KnownAndExtraValue();
    }
  }
}
