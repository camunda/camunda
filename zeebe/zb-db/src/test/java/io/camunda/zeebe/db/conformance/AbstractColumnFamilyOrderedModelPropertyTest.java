/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.KeyVisitor;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.agrona.ExpandableArrayBuffer;
import org.assertj.core.util.Files;

/**
 * Compares a column family against an <em>ordered</em> model. The model is a {@link TreeMap} keyed
 * by the serialized key bytes under unsigned bytewise comparison, which is the order every backend
 * has to reproduce. Unlike a {@code HashMap} model this pins the order of full scans, scans from a
 * start key and reverse scans, for keys including negative numbers and strings of mixed length.
 */
@Tag("randomized")
public abstract class AbstractColumnFamilyOrderedModelPropertyTest {

  private static final long[] EDGE_LONGS = {0, 1, 2, -1, -2, Long.MAX_VALUE, Long.MIN_VALUE};
  private static final String[] STRINGS = {"", "a", "b", "zz", "aa", "ab", "aaa", "zzz", "aä"};

  protected abstract ZeebeDbFactory<DefaultColumnFamily> factory();

  private ZeebeDb<DefaultColumnFamily> zeebeDb;
  private Runnable clearColumnFamily = () -> {};
  private final TreeMap<byte[], Long> model = new TreeMap<>(Arrays::compareUnsigned);

  @BeforeProperty
  private void setup() {
    zeebeDb = factory().createDb(Files.newTemporaryFolder());
    clearColumnFamily = () -> {};
  }

  @AfterTry
  private void cleanup() {
    clearColumnFamily.run();
    model.clear();
  }

  @AfterProperty
  private void teardown() throws Exception {
    zeebeDb.close();
  }

  @Property
  void longKeysScanInUnsignedByteOrder(
      @ForAll("longOperations") final List<Op> operations, @ForAll("anyLong") final long probe) {
    final ColumnFamily<DbLong, DbLong> columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), new DbLong(), new DbLong());
    check(
        columnFamily,
        operations,
        dbLong(probe),
        DbLong::new,
        (key, op) -> key.wrapLong(op.first()));
  }

  @Property
  void compositeKeysScanInUnsignedByteOrder(
      @ForAll("compositeOperations") final List<Op> operations,
      @ForAll("anyString") final String probeString,
      @ForAll("anyLong") final long probeLong) {
    final ColumnFamily<DbCompositeKey<DbString, DbLong>, DbLong> columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT,
            zeebeDb.createContext(),
            new DbCompositeKey<>(new DbString(), new DbLong()),
            new DbLong());
    check(
        columnFamily,
        operations,
        new DbCompositeKey<>(dbString(probeString), dbLong(probeLong)),
        () -> new DbCompositeKey<>(new DbString(), new DbLong()),
        (key, op) -> {
          key.first().wrapString(op.string());
          key.second().wrapLong(op.first());
        });
  }

  private <K extends DbKey> void check(
      final ColumnFamily<K, DbLong> columnFamily,
      final List<Op> operations,
      final K probe,
      final Supplier<K> newKey,
      final BiConsumer<K, Op> keyBinder) {
    clearColumnFamily = () -> columnFamily.forEach((k, v) -> columnFamily.deleteExisting(k));
    final byte[] probeBytes = serialize(probe);

    // given
    for (final Op op : operations) {
      final K key = newKey.get();
      keyBinder.accept(key, op);
      final byte[] bytes = serialize(key);
      if (op.delete()) {
        model.remove(bytes);
        columnFamily.deleteIfExists(key);
      } else {
        model.put(bytes, op.value());
        columnFamily.upsert(key, dbLong(op.value()));
      }
    }

    // when
    final List<byte[]> scanned = new ArrayList<>();
    columnFamily.forEach((key, value) -> scanned.add(serialize(key)));
    final List<byte[]> scannedFromProbe = new ArrayList<>();
    columnFamily.whileTrue(
        probe,
        (key, value) -> {
          scannedFromProbe.add(serialize(key));
          return true;
        });
    final List<byte[]> scannedReverse = new ArrayList<>();
    columnFamily.whileTrueReverse(
        probe,
        (key, value) -> {
          scannedReverse.add(serialize(key));
          return true;
        });
    final List<byte[]> scannedKeysOnly = new ArrayList<>();
    final KeyVisitor<K> keyVisitor =
        key -> {
          scannedKeysOnly.add(serialize(key));
          return true;
        };
    columnFamily.whileTrue(keyVisitor);

    // then
    assertThat(scanned).containsExactlyElementsOf(model.keySet());
    assertThat(scannedKeysOnly).containsExactlyElementsOf(model.keySet());
    assertThat(scannedFromProbe)
        .containsExactlyElementsOf(model.tailMap(probeBytes, true).keySet());
    assertThat(scannedReverse)
        .containsExactlyElementsOf(model.headMap(probeBytes, true).descendingMap().keySet());
  }

  @Provide
  Arbitrary<Long> anyLong() {
    return Arbitraries.oneOf(Arbitraries.longs(), Arbitraries.of(toList(EDGE_LONGS)));
  }

  @Provide
  Arbitrary<String> anyString() {
    return Arbitraries.of(STRINGS);
  }

  @Provide
  Arbitrary<List<Op>> longOperations() {
    return operation(Arbitraries.just("")).list().ofMaxSize(60);
  }

  @Provide
  Arbitrary<List<Op>> compositeOperations() {
    return operation(Arbitraries.of(STRINGS)).list().ofMaxSize(60);
  }

  private Arbitrary<Op> operation(final Arbitrary<String> strings) {
    return Combinators.combine(
            Arbitraries.of(true, false, false), anyLong(), strings, Arbitraries.longs())
        .as(Op::new);
  }

  private static List<Long> toList(final long[] values) {
    return Arrays.stream(values).boxed().toList();
  }

  private static DbLong dbLong(final long value) {
    final var key = new DbLong();
    key.wrapLong(value);
    return key;
  }

  private static DbString dbString(final String value) {
    final var key = new DbString();
    key.wrapString(value);
    return key;
  }

  private static byte[] serialize(final DbKey key) {
    final var buffer = new ExpandableArrayBuffer();
    final int length = key.write(buffer, 0);
    final byte[] bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return bytes;
  }

  /** A delete or upsert; {@code first} is the numeric key component, {@code string} the other. */
  record Op(boolean delete, long first, String string, long value) {}
}
