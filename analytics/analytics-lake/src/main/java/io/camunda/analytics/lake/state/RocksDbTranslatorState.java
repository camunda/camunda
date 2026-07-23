/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.state;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.camunda.analytics.lake.state.TranslatorState.ObjectLifecycle;
import io.camunda.analytics.lake.state.TranslatorState.ObjectSightingList;
import io.camunda.analytics.lake.state.TranslatorState.VariantAccumulator;
import io.camunda.analytics.lake.state.TranslatorState.VariantName;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * RocksDB-backed {@link TranslatorState} over the {@code event-bridge-streaming} state library: one
 * RocksDB under the state directory, one column family per {@link LakeColumnFamilies} (including
 * the variant-k1 accumulator and name-map column families). Writes are per-call (each put/delete
 * its own transaction), so durability is RocksDB's default WAL behavior — no batching or checkpoint
 * coupling, because this store persists no offsets (the lake's snapshot summary is the offset
 * authority) and every operation is a last-write-wins put or an idempotent delete, so replay from
 * the last lake-committed offset converges.
 *
 * <p>Single-threaded: the owning translator is the only caller, so the mutation flyweights are
 * reused across calls. Values returned by the store are copied out into immutable records before
 * the next store operation, per the store's flyweight contract.
 */
public final class RocksDbTranslatorState implements TranslatorState {

  private final RocksDbStateStoreProvider<LakeColumnFamilies> provider;
  private final KeyValueStore<DbLong, OpenInstanceValue> instances;
  private final KeyValueStore<DbLong, OpenElementValue> elements;
  private final KeyValueStore<DbBytes, DbString> variables;
  private final KeyValueStore<DbLong, VariantAccumulatorValue> variantAccumulators;
  private final KeyValueStore<DbBytes, VariantNameValue> variantNames;

  private final KeyValueStore<DbBytes, FlowEndpointsValue> flowEndpoints;
  private final KeyValueStore<DbLong, ObjectSightingListValue> objectSightings;
  private final KeyValueStore<DbBytes, ObjectLifecycleValue> objectLifecycle;

  // Mutation flyweights — reused across calls on the single translator thread.
  private final DbLong instanceKey = new DbLong();
  private final OpenInstanceValue instanceValue = new OpenInstanceValue();
  private final DbLong elementKey = new DbLong();
  private final OpenElementValue elementValue = new OpenElementValue();
  private final DbBytes variableKey = new DbBytes();
  private final DbString variableValue = new DbString();
  private final DbBytes variablePrefix = new DbBytes();
  private final DbLong variantInstanceKey = new DbLong();
  private final VariantAccumulatorValue variantAccumulatorValue = new VariantAccumulatorValue();
  private final DbBytes variantNameKey = new DbBytes();
  private final VariantNameValue variantNameValue = new VariantNameValue();

  private final DbBytes flowEndpointsKey = new DbBytes();
  private final FlowEndpointsValue flowEndpointsValue = new FlowEndpointsValue();
  private final DbLong objectSightingsInstanceKey = new DbLong();
  private final ObjectSightingListValue objectSightingListValue = new ObjectSightingListValue();
  private final DbBytes objectLifecycleKey = new DbBytes();
  private final ObjectLifecycleValue objectLifecycleValue = new ObjectLifecycleValue();

  public RocksDbTranslatorState(final Path stateDir) {
    provider = RocksDbStateStoreProvider.open(stateDir.toFile(), new SimpleMeterRegistry());
    // Dedicated fresh flyweights per store (the provider binds the first ones it is given).
    instances =
        provider.keyValueStore(
            LakeColumnFamilies.OPEN_INSTANCES, new DbLong(), new OpenInstanceValue());
    elements =
        provider.keyValueStore(
            LakeColumnFamilies.OPEN_ELEMENTS, new DbLong(), new OpenElementValue());
    variables = provider.keyValueStore(LakeColumnFamilies.VARIABLES, new DbBytes(), new DbString());
    variantAccumulators =
        provider.keyValueStore(
            LakeColumnFamilies.VARIANT_ACCUMULATORS, new DbLong(), new VariantAccumulatorValue());
    variantNames =
        provider.keyValueStore(
            LakeColumnFamilies.VARIANT_NAMES, new DbBytes(), new VariantNameValue());

    flowEndpoints =
        provider.keyValueStore(
            LakeColumnFamilies.FLOW_ENDPOINTS, new DbBytes(), new FlowEndpointsValue());
    objectSightings =
        provider.keyValueStore(
            LakeColumnFamilies.OBJECT_SIGHTINGS, new DbLong(), new ObjectSightingListValue());
    objectLifecycle =
        provider.keyValueStore(
            LakeColumnFamilies.OBJECT_LIFECYCLE, new DbBytes(), new ObjectLifecycleValue());
  }

  @Override
  public void putInstance(final long instanceKey, final OpenInstance instance) {
    this.instanceKey.wrapLong(instanceKey);
    instances.put(this.instanceKey, instanceValue.set(instance));
  }

  @Override
  public OpenInstance getInstance(final long instanceKey) {
    this.instanceKey.wrapLong(instanceKey);
    return instances.get(this.instanceKey).map(OpenInstanceValue::toRecord).orElse(null);
  }

  @Override
  public void deleteInstance(final long instanceKey) {
    this.instanceKey.wrapLong(instanceKey);
    instances.delete(this.instanceKey);
  }

  @Override
  public void putElement(final long elementKey, final OpenElement element) {
    this.elementKey.wrapLong(elementKey);
    elements.put(this.elementKey, elementValue.set(element));
  }

  @Override
  public OpenElement getElement(final long elementKey) {
    this.elementKey.wrapLong(elementKey);
    return elements.get(this.elementKey).map(OpenElementValue::toRecord).orElse(null);
  }

  @Override
  public void deleteElement(final long elementKey) {
    this.elementKey.wrapLong(elementKey);
    elements.delete(this.elementKey);
  }

  @Override
  public void putVariable(final long instanceKey, final String name, final String valueJson) {
    variableKey.wrapBytes(variableKey(instanceKey, name));
    // Wrap the UTF-8 bytes directly (DbString#wrapString would use the platform charset).
    variableValue.wrapBuffer(new UnsafeBuffer(valueJson.getBytes(UTF_8)));
    variables.put(variableKey, variableValue);
  }

  @Override
  public Map<String, String> variablesOf(final long instanceKey) {
    variablePrefix.wrapBytes(instancePrefix(instanceKey));
    final Map<String, String> result = new LinkedHashMap<>();
    variables.prefixScan(
        variablePrefix,
        (key, value) -> result.put(variableName(key.getBytes()), decode(value.getBuffer())));
    return result;
  }

  @Override
  public void deleteVariablesOf(final long instanceKey) {
    variablePrefix.wrapBytes(instancePrefix(instanceKey));
    final List<byte[]> keys = new ArrayList<>();
    // Collect keys first, then delete: the scan iterator must not be mutated mid-iteration.
    variables.prefixScanKeys(variablePrefix, key -> keys.add(key.getBytes().clone()));
    for (final byte[] key : keys) {
      variableKey.wrapBytes(key);
      variables.delete(variableKey);
    }
  }

  @Override
  public void putVariantAccumulator(final long instanceKey, final VariantAccumulator accumulator) {
    variantInstanceKey.wrapLong(instanceKey);
    variantAccumulators.put(variantInstanceKey, variantAccumulatorValue.set(accumulator));
  }

  @Override
  public VariantAccumulator getVariantAccumulator(final long instanceKey) {
    variantInstanceKey.wrapLong(instanceKey);
    return variantAccumulators
        .get(variantInstanceKey)
        .map(VariantAccumulatorValue::toRecord)
        .orElse(null);
  }

  @Override
  public void deleteVariantAccumulator(final long instanceKey) {
    variantInstanceKey.wrapLong(instanceKey);
    variantAccumulators.delete(variantInstanceKey);
  }

  @Override
  public void putVariantName(final String bpmnProcessId, final int h32, final VariantName name) {
    variantNameKey.wrapBytes(variantNameKey(bpmnProcessId, h32));
    variantNames.put(variantNameKey, variantNameValue.set(name));
  }

  @Override
  public VariantName getVariantName(final String bpmnProcessId, final int h32) {
    variantNameKey.wrapBytes(variantNameKey(bpmnProcessId, h32));
    return variantNames.get(variantNameKey).map(VariantNameValue::toRecord).orElse(null);
  }

  @Override
  public void putFlowEndpoints(
      final long processDefinitionKey, final String flowId, final FlowEndpoints endpoints) {
    flowEndpointsKey.wrapBytes(flowEndpointsKey(processDefinitionKey, flowId));
    flowEndpoints.put(flowEndpointsKey, flowEndpointsValue.set(endpoints));
  }

  @Override
  public FlowEndpoints flowEndpoints(final long processDefinitionKey, final String flowId) {
    flowEndpointsKey.wrapBytes(flowEndpointsKey(processDefinitionKey, flowId));
    return flowEndpoints.get(flowEndpointsKey).map(FlowEndpointsValue::toRecord).orElse(null);
  }

  @Override
  public void putObjectSightings(final long instanceKey, final ObjectSightingList sightings) {
    objectSightingsInstanceKey.wrapLong(instanceKey);
    objectSightings.put(objectSightingsInstanceKey, objectSightingListValue.set(sightings));
  }

  @Override
  public ObjectSightingList getObjectSightings(final long instanceKey) {
    objectSightingsInstanceKey.wrapLong(instanceKey);
    return objectSightings
        .get(objectSightingsInstanceKey)
        .map(ObjectSightingListValue::toRecord)
        .orElse(null);
  }

  @Override
  public void deleteObjectSightings(final long instanceKey) {
    objectSightingsInstanceKey.wrapLong(instanceKey);
    objectSightings.delete(objectSightingsInstanceKey);
  }

  @Override
  public void putObjectLifecycle(
      final String objectType, final String objectId, final ObjectLifecycle lifecycle) {
    objectLifecycleKey.wrapBytes(objectLifecycleKey(objectType, objectId));
    objectLifecycle.put(objectLifecycleKey, objectLifecycleValue.set(lifecycle));
  }

  @Override
  public ObjectLifecycle getObjectLifecycle(final String objectType, final String objectId) {
    objectLifecycleKey.wrapBytes(objectLifecycleKey(objectType, objectId));
    return objectLifecycle.get(objectLifecycleKey).map(ObjectLifecycleValue::toRecord).orElse(null);
  }

  @Override
  public int sweepObjectLifecycleTombstones(final long cutoffMs) {
    // Collect first, then delete -- the scan iterator must not be mutated mid-iteration (same
    // pattern as #deleteVariablesOf). A fresh ObjectLifecycleValue per matching entry: the
    // provider's own flyweight is only valid until the next store call, and #wrap has already run
    // by the time the visitor sees it, so reading its fields here (not holding the flyweight
    // itself) is safe.
    final List<byte[]> toDelete = new ArrayList<>();
    objectLifecycle.forEach(
        (key, value) -> {
          final ObjectLifecycle lifecycle = value.toRecord();
          if (lifecycle.status() == TranslatorState.LifecycleStatus.CLOSED_TOMBSTONE
              && lifecycle.closedAtMs() < cutoffMs) {
            toDelete.add(key.getBytes().clone());
          }
        });
    for (final byte[] key : toDelete) {
      objectLifecycleKey.wrapBytes(key);
      objectLifecycle.delete(objectLifecycleKey);
    }
    return toDelete.size();
  }

  @Override
  public void forEachOpenInstance(final BiConsumer<Long, OpenInstance> consumer) {
    instances.forEach((key, value) -> consumer.accept(key.getValue(), value.toRecord()));
  }

  @Override
  public void forEachOpenElement(final BiConsumer<Long, OpenElement> consumer) {
    elements.forEach((key, value) -> consumer.accept(key.getValue(), value.toRecord()));
  }

  @Override
  public void close() {
    try {
      provider.close();
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to close lake translator state", e);
    }
  }

  private static byte[] instancePrefix(final long instanceKey) {
    return ByteBuffer.allocate(Long.BYTES).putLong(instanceKey).array();
  }

  /**
   * {@code bpmnProcessId(utf8) ++ h32(4, big-endian)}. No length prefix is needed (unlike a
   * variable-length field followed by another variable-length one, which would be genuinely
   * ambiguous): {@code h32} is always exactly 4 bytes, so two keys can only be byte-identical if
   * their process id portions are also the same length — at which point byte-identical keys mean
   * byte-identical process ids and byte-identical {@code h32}s, i.e. the same pair, not a
   * collision.
   */
  private static byte[] variantNameKey(final String bpmnProcessId, final int h32) {
    final byte[] processIdUtf8 = bpmnProcessId.getBytes(UTF_8);
    return ByteBuffer.allocate(processIdUtf8.length + Integer.BYTES)
        .put(processIdUtf8)
        .putInt(h32)
        .array();
  }

  private static byte[] variableKey(final long instanceKey, final String name) {
    final byte[] nameUtf8 = name.getBytes(UTF_8);
    return ByteBuffer.allocate(Long.BYTES + nameUtf8.length)
        .putLong(instanceKey)
        .put(nameUtf8)
        .array();
  }

  /**
   * {@code length(objectType)(4) ++ objectTypeUtf8 ++ objectIdUtf8}: only the first component is
   * length-prefixed. That suffices for an unambiguous key even though both components are
   * variable-length, because {@code objectId} is the LAST field — once the prefix says exactly how
   * many bytes belong to {@code objectType}, every remaining byte in the key (whatever its length)
   * belongs to {@code objectId} by construction; there is no second variable-length field after it
   * that a missing length prefix could ever be ambiguous with. Mirrors {@link #variantNameKey}'s
   * own reasoning for why that key needs no prefix at all (a fixed-width suffix), just for a
   * variable-width suffix instead of a fixed one.
   */
  private static byte[] objectLifecycleKey(final String objectType, final String objectId) {
    final byte[] typeUtf8 = objectType.getBytes(UTF_8);
    final byte[] idUtf8 = objectId.getBytes(UTF_8);
    return ByteBuffer.allocate(Integer.BYTES + typeUtf8.length + idUtf8.length)
        .putInt(typeUtf8.length)
        .put(typeUtf8)
        .put(idUtf8)
        .array();
  }

  private static byte[] flowEndpointsKey(final long processDefinitionKey, final String flowId) {
    final byte[] flowIdUtf8 = flowId.getBytes(UTF_8);
    return ByteBuffer.allocate(Long.BYTES + flowIdUtf8.length)
        .putLong(processDefinitionKey)
        .put(flowIdUtf8)
        .array();
  }

  private static String variableName(final byte[] key) {
    return new String(key, Long.BYTES, key.length - Long.BYTES, UTF_8);
  }

  private static String decode(final DirectBuffer buffer) {
    final byte[] bytes = new byte[buffer.capacity()];
    buffer.getBytes(0, bytes);
    return new String(bytes, UTF_8);
  }
}
