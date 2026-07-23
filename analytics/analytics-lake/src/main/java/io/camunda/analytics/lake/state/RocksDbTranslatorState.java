/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.state;

import static java.nio.charset.StandardCharsets.UTF_8;

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
 * RocksDB under the state directory, one column family per {@link LakeColumnFamilies}. Writes are
 * per-call (each put/delete its own transaction), so durability is RocksDB's default WAL behavior —
 * no batching or checkpoint coupling, because this store persists no offsets (the lake's snapshot
 * summary is the offset authority) and every operation is a last-write-wins put or an idempotent
 * delete, so replay from the last lake-committed offset converges.
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
  private final KeyValueStore<DbBytes, FlowEndpointsValue> flowEndpoints;

  // Mutation flyweights — reused across calls on the single translator thread.
  private final DbLong instanceKey = new DbLong();
  private final OpenInstanceValue instanceValue = new OpenInstanceValue();
  private final DbLong elementKey = new DbLong();
  private final OpenElementValue elementValue = new OpenElementValue();
  private final DbBytes variableKey = new DbBytes();
  private final DbString variableValue = new DbString();
  private final DbBytes variablePrefix = new DbBytes();
  private final DbBytes flowEndpointsKey = new DbBytes();
  private final FlowEndpointsValue flowEndpointsValue = new FlowEndpointsValue();

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
    flowEndpoints =
        provider.keyValueStore(
            LakeColumnFamilies.FLOW_ENDPOINTS, new DbBytes(), new FlowEndpointsValue());
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

  private static byte[] variableKey(final long instanceKey, final String name) {
    final byte[] nameUtf8 = name.getBytes(UTF_8);
    return ByteBuffer.allocate(Long.BYTES + nameUtf8.length)
        .putLong(instanceKey)
        .put(nameUtf8)
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
