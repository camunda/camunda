/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.UntypedDbValueTarget;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import io.camunda.zeebe.db.impl.inmemory.InMemoryZeebeDb;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class InMemoryColumnFamilyObjectBackedValueTest {

  private ZeebeDb<DefaultColumnFamily> zeebeDb;
  private ColumnFamily<DbLong, KnownOnlyValue> columnFamily;
  private DbLong key;

  @BeforeEach
  void setUp() {
    zeebeDb = new InMemoryZeebeDb<>();
    key = new DbLong();
    columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), key, new KnownOnlyValue());
  }

  @AfterEach
  void tearDown() throws Exception {
    zeebeDb.close();
  }

  @Test
  void shouldPreserveUndeclaredPropertiesOnPlainGet() {
    // given
    key.wrapLong(1L);
    columnFamily.insert(key, wrapKnownOnlyValueWithExtraProperty("known-1", "extra-1"));

    // when
    key.wrapLong(1L);
    final var result = columnFamily.get(key);

    // then
    assertThat(result).isNotNull();
    final var decodedResult = decode(result);
    assertThat(decodedResult.getKnown()).isEqualTo("known-1");
    assertThat(decodedResult.getExtra()).isEqualTo("extra-1");
  }

  @Test
  void shouldPreserveUndeclaredPropertiesOnSupplierGet() {
    // given
    key.wrapLong(1L);
    columnFamily.insert(key, wrapKnownOnlyValueWithExtraProperty("known-1", "extra-1"));

    // when
    key.wrapLong(1L);
    final var result = columnFamily.get(key, KnownOnlyValue::new);

    // then
    assertThat(result).isNotNull();
    final var decodedResult = decode(result);
    assertThat(decodedResult.getKnown()).isEqualTo("known-1");
    assertThat(decodedResult.getExtra()).isEqualTo("extra-1");
  }

  @Test
  void shouldClearUndeclaredPropertiesBetweenPlainGets() {
    // given
    key.wrapLong(1L);
    columnFamily.insert(key, wrapKnownOnlyValueWithExtraProperty("known-1", "extra-1"));

    key.wrapLong(2L);
    columnFamily.insert(key, new KnownOnlyValue().setKnown("known-2"));

    // when
    key.wrapLong(1L);
    final var firstResult = decode(columnFamily.get(key));

    key.wrapLong(2L);
    final var secondResult = decode(columnFamily.get(key));

    // then
    assertThat(firstResult.getKnown()).isEqualTo("known-1");
    assertThat(firstResult.getExtra()).isEqualTo("extra-1");
    assertThat(secondResult.getKnown()).isEqualTo("known-2");
    assertThat(secondResult.getExtra()).isEmpty();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Test
  void shouldReadIntoUntypedTargetsThroughSerializationFallback() {
    // given
    final ColumnFamily<DbLong, DbValue> rawColumnFamily =
        (ColumnFamily)
            zeebeDb.createColumnFamily(
                DefaultColumnFamily.DEFAULT,
                zeebeDb.createContext(),
                new DbLong(),
                new KnownOnlyValue());

    final var rawKey = new DbLong();
    rawKey.wrapLong(1L);
    rawColumnFamily.insert(rawKey, wrapKnownOnlyValueWithExtraProperty("known-1", "extra-1"));

    // when
    rawKey.wrapLong(1L);
    final var result = rawColumnFamily.get(rawKey, UntypedValue::new);

    // then
    assertThat(result).isNotNull();
    final var decodedResult = decode(result);
    assertThat(decodedResult.getKnown()).isEqualTo("known-1");
    assertThat(decodedResult.getExtra()).isEqualTo("extra-1");
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

    private KnownOnlyValue setKnown(final String known) {
      knownProp.setValue(known);
      return this;
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

  private static final class UntypedValue extends UnpackedObject
      implements DbValue, UntypedDbValueTarget {

    private UntypedValue() {
      super(0);
    }

    @Override
    public void copyTo(final DbValue target) {
      super.copyTo((UntypedValue) target);
    }

    @Override
    public UntypedValue newInstance() {
      return new UntypedValue();
    }
  }
}
