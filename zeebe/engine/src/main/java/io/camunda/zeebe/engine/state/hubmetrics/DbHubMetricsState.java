/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.hubmetrics;

import com.tdunning.math.stats.MergingDigest;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.engine.state.mutable.MutableHubMetricsState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;

public class DbHubMetricsState implements MutableHubMetricsState {

  private final Map<ProcessMetricsKey, ProcessMetricsValue> processMetricsCache;
  private final Map<ElementMetricsKey, ElementMetricsValue> elementMetricsCache;
  private final Map<ProcessMetricsKey, MergingDigest> processDigestCache;
  private final Map<ElementMetricsKey, MergingDigest> elementDigestCache;

  private final DbString tenantIdKey;
  private final DbString processId;
  private final DbInt processVersion;
  private final DbCompositeKey<DbString, DbInt> idAndVersionKey;
  private final ProcessMetricsValue processMetricsValue;
  private final DbTenantAwareKey<DbCompositeKey<DbString, DbInt>> tenantAwareProcessIdAndVersionKey;
  private final ColumnFamily<DbTenantAwareKey<DbCompositeKey<DbString, DbInt>>, ProcessMetricsValue>
      processByTenantIDAndVersionMetricsColumnFamily;

  private final DbString elementId;
  private final DbCompositeKey<DbInt, DbString> versionAndElementIdKey;
  private final DbCompositeKey<DbString, DbCompositeKey<DbInt, DbString>>
      idAndVersionAndElementIdKey;
  private final ElementMetricsValue elementMetricsValue;

  private final DbTenantAwareKey<DbCompositeKey<DbString, DbCompositeKey<DbInt, DbString>>>
      tenantAwareProcessIdVersionAndElementIdKey;
  private final ColumnFamily<
          DbTenantAwareKey<DbCompositeKey<DbString, DbCompositeKey<DbInt, DbString>>>,
          ElementMetricsValue>
      processByTenantIDVersionAndElementMetricsColumnFamily;

  public DbHubMetricsState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    tenantIdKey = new DbString();
    processId = new DbString();
    processVersion = new DbInt();
    idAndVersionKey = new DbCompositeKey<>(processId, processVersion);
    tenantAwareProcessIdAndVersionKey =
        new DbTenantAwareKey<>(tenantIdKey, idAndVersionKey, PlacementType.PREFIX);
    processMetricsValue = new ProcessMetricsValue();

    processByTenantIDAndVersionMetricsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_METRICS,
            transactionContext,
            tenantAwareProcessIdAndVersionKey,
            processMetricsValue);

    elementId = new DbString();
    versionAndElementIdKey = new DbCompositeKey<>(processVersion, elementId);
    idAndVersionAndElementIdKey = new DbCompositeKey<>(processId, versionAndElementIdKey);
    tenantAwareProcessIdVersionAndElementIdKey =
        new DbTenantAwareKey<>(tenantIdKey, idAndVersionAndElementIdKey, PlacementType.PREFIX);
    elementMetricsValue = new ElementMetricsValue();

    processByTenantIDVersionAndElementMetricsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.ELEMENT_METRICS,
            transactionContext,
            tenantAwareProcessIdVersionAndElementIdKey,
            elementMetricsValue);

    processMetricsCache = new HashMap<>();
    processDigestCache = new HashMap<>();
    populateProcessMetricsCache();
    elementMetricsCache = new HashMap<>();
    elementDigestCache = new HashMap<>();
    populateElementMetricsCache();
  }

  private void populateProcessMetricsCache() {
    processByTenantIDAndVersionMetricsColumnFamily.forEach(
        (key, value) -> {
          final var metricsKey =
              new ProcessMetricsKey(
                  key.tenantKey().toString(),
                  key.wrappedKey().first().toString(),
                  key.wrappedKey().second().getValue());
          processMetricsCache.put(metricsKey, new ProcessMetricsValue().wrap(value));
          final var digestBuffer = value.getDigest().byteBuffer();
          if (digestBuffer != null) {
            final var digest = MergingDigest.fromBytes(digestBuffer);
            processDigestCache.put(metricsKey, digest);
          }
        });
  }

  private void populateElementMetricsCache() {
    processByTenantIDVersionAndElementMetricsColumnFamily.forEach(
        (key, value) -> {
          final var metricsKey =
              new ElementMetricsKey(
                  key.tenantKey().toString(),
                  key.wrappedKey().first().toString(),
                  key.wrappedKey().second().first().getValue(),
                  key.wrappedKey().second().second().toString());
          elementMetricsCache.put(metricsKey, new ElementMetricsValue().wrap(value));
          final var digestBuffer = value.getDigest().byteBuffer();
          if (digestBuffer != null) {
            final var digest = MergingDigest.fromBytes(digestBuffer);
            elementDigestCache.put(metricsKey, digest);
          }
        });
  }

  @Override
  public void updateOnProcessInstanceCreated(final ProcessInstanceRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    processId.wrapBuffer(record.getBpmnProcessIdBuffer());
    processVersion.wrapInt(record.getVersion());

    final var key =
        new ProcessMetricsKey(record.getTenantId(), record.getBpmnProcessId(), record.getVersion());
    final var cachedValue = processMetricsCache.get(key);

    if (cachedValue != null) {
      cachedValue.incrementAbsolute();
      cachedValue.incrementCreated();
      processByTenantIDAndVersionMetricsColumnFamily.upsert(
          tenantAwareProcessIdAndVersionKey, cachedValue);
      return;
    }

    final var v = new ProcessMetricsValue();
    v.incrementAbsolute();
    v.incrementCreated();
    processByTenantIDAndVersionMetricsColumnFamily.upsert(tenantAwareProcessIdAndVersionKey, v);
    processMetricsCache.put(key, new ProcessMetricsValue().wrap(v));
  }

  @Override
  public void updateOnProcessInstanceCompleted(final ProcessInstanceRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    processId.wrapBuffer(record.getBpmnProcessIdBuffer());
    processVersion.wrapInt(record.getVersion());

    final var key =
        new ProcessMetricsKey(record.getTenantId(), record.getBpmnProcessId(), record.getVersion());
    final var cachedValue = processMetricsCache.get(key);

    if (cachedValue != null) {
      cachedValue.decrementAbsolute();
      cachedValue.incrementCompleted();
      final var duration = record.getEndTime() - record.getStartTime();

      final var digest = processDigestCache.computeIfAbsent(key, k -> new MergingDigest(100));
      digest.add(duration);
      final var buffer = ByteBuffer.allocate(digest.byteSize());
      digest.asBytes(buffer);
      cachedValue.setDigest(new UnsafeBuffer(buffer));

      cachedValue.addDuration(duration);
      cachedValue.setMaxDurationIfHigher(duration);
      cachedValue.setMinDurationIfLower(duration);
      processByTenantIDAndVersionMetricsColumnFamily.upsert(
          tenantAwareProcessIdAndVersionKey, cachedValue);
      return;
    }

    final var v = new ProcessMetricsValue();
    v.decrementAbsolute();
    v.incrementCompleted();
    final var duration = record.getEndTime() - record.getStartTime();

    final var digest = processDigestCache.computeIfAbsent(key, k -> new MergingDigest(100));
    digest.add(duration);
    final var buffer = ByteBuffer.allocate(digest.byteSize());
    digest.asBytes(buffer);
    v.setDigest(new UnsafeBuffer(buffer));

    v.addDuration(duration);
    v.setMaxDurationIfHigher(duration);
    v.setMinDurationIfLower(duration);
    processByTenantIDAndVersionMetricsColumnFamily.upsert(tenantAwareProcessIdAndVersionKey, v);
    processMetricsCache.put(key, new ProcessMetricsValue().wrap(v));
  }

  @Override
  public void updateOnElementCreated(final ProcessInstanceRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    processId.wrapBuffer(record.getBpmnProcessIdBuffer());
    processVersion.wrapInt(record.getVersion());
    elementId.wrapBuffer(record.getElementIdBuffer());

    final var key =
        new ElementMetricsKey(
            record.getTenantId(),
            record.getBpmnProcessId(),
            record.getVersion(),
            record.getElementId());
    final var cachedValue = elementMetricsCache.get(key);

    if (cachedValue != null) {
      cachedValue.incrementCreated();
      processByTenantIDVersionAndElementMetricsColumnFamily.upsert(
          tenantAwareProcessIdVersionAndElementIdKey, cachedValue);
      return;
    }

    final var v = new ElementMetricsValue();
    v.incrementCreated();
    processByTenantIDVersionAndElementMetricsColumnFamily.upsert(
        tenantAwareProcessIdVersionAndElementIdKey, v);
    elementMetricsCache.put(key, new ElementMetricsValue().wrap(v));
  }

  @Override
  public void updateOnElementCompleted(final ProcessInstanceRecord record) {
    tenantIdKey.wrapString(record.getTenantId());
    processId.wrapBuffer(record.getBpmnProcessIdBuffer());
    processVersion.wrapInt(record.getVersion());
    elementId.wrapBuffer(record.getElementIdBuffer());

    final var key =
        new ElementMetricsKey(
            record.getTenantId(),
            record.getBpmnProcessId(),
            record.getVersion(),
            record.getElementId());
    final var cachedValue = elementMetricsCache.get(key);

    if (cachedValue != null) {
      cachedValue.incrementCompleted();
      final var duration = record.getEndTime() - record.getStartTime();

      final var digest = elementDigestCache.computeIfAbsent(key, k -> new MergingDigest(100));
      digest.add(duration);
      final var buffer = ByteBuffer.allocate(digest.byteSize());
      digest.asBytes(buffer);
      cachedValue.setDigest(new UnsafeBuffer(buffer));

      cachedValue.addDuration(duration);
      cachedValue.setMaxDurationIfHigher(duration);
      cachedValue.setMinDurationIfLower(duration);
      processByTenantIDVersionAndElementMetricsColumnFamily.upsert(
          tenantAwareProcessIdVersionAndElementIdKey, cachedValue);
      return;
    }

    final var v = new ElementMetricsValue();
    v.incrementCompleted();
    final var duration = record.getEndTime() - record.getStartTime();

    final var digest = elementDigestCache.computeIfAbsent(key, k -> new MergingDigest(100));
    digest.add(duration);
    final var buffer = ByteBuffer.allocate(digest.byteSize());
    digest.asBytes(buffer);
    v.setDigest(new UnsafeBuffer(buffer));

    v.addDuration(duration);
    v.setMaxDurationIfHigher(duration);
    v.setMinDurationIfLower(duration);
    processByTenantIDVersionAndElementMetricsColumnFamily.upsert(
        tenantAwareProcessIdVersionAndElementIdKey, v);
    elementMetricsCache.put(key, new ElementMetricsValue().wrap(v));
  }

  @Override
  public void reset() {
    processMetricsCache.forEach(
        (k, v) -> {
          v.setCompleted(0);
          v.setCreated(0);
          v.setDigest(new UnsafeBuffer(0, 0));
          tenantIdKey.wrapString(k.tenantId());
          processId.wrapString(k.processId());
          processVersion.wrapInt(k.version());
          processByTenantIDAndVersionMetricsColumnFamily.upsert(
              tenantAwareProcessIdAndVersionKey, v);
        });
    processDigestCache.clear();

    elementMetricsCache.forEach(
        (k, v) -> {
          tenantIdKey.wrapString(k.tenantId());
          processId.wrapString(k.processId());
          processVersion.wrapInt(k.version());
          elementId.wrapString(k.elementId());
          processByTenantIDVersionAndElementMetricsColumnFamily.deleteIfExists(
              tenantAwareProcessIdVersionAndElementIdKey);
        });
    elementMetricsCache.clear();
    elementDigestCache.clear();
  }

  record ProcessMetricsKey(String tenantId, String processId, int version) {}

  record ElementMetricsKey(String tenantId, String processId, int version, String elementId) {}
}
