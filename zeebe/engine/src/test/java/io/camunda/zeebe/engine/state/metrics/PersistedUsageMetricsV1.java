/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.metrics;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class PersistedUsageMetricsV1 extends UnpackedObject implements DbValue {

  public static final int TIME_NOT_SET = -1;

  private final LongProperty fromTimeProp = new LongProperty("fromTime", TIME_NOT_SET);
  private final LongProperty toTimeProp = new LongProperty("toTime", TIME_NOT_SET);
  private final DocumentProperty tenantRPIMapProp = new DocumentProperty("tenantRPI");
  private final DocumentProperty tenantEDIMapProp = new DocumentProperty("tenantEDI");
  private final DocumentProperty tenantTUMapProp = new DocumentProperty("tenantTU");

  public PersistedUsageMetricsV1() {
    super(5);
    declareProperty(fromTimeProp)
        .declareProperty(toTimeProp)
        .declareProperty(tenantRPIMapProp)
        .declareProperty(tenantEDIMapProp)
        .declareProperty(tenantTUMapProp);
  }

  public long getFromTime() {
    return fromTimeProp.getValue();
  }

  public PersistedUsageMetricsV1 setFromTime(final long fromTime) {
    fromTimeProp.setValue(fromTime);
    return this;
  }

  public long getToTime() {
    return toTimeProp.getValue();
  }

  public PersistedUsageMetricsV1 setToTime(final long toTime) {
    toTimeProp.setValue(toTime);
    return this;
  }

  public DirectBuffer getTenantRPIMapValue() {
    return tenantRPIMapProp.getValue();
  }

  public Map<String, Long> getTenantRPIMap() {
    return MsgPackConverter.convertToLongMap(tenantRPIMapProp.getValue());
  }

  public PersistedUsageMetricsV1 setTenantRPIMap(final Map<String, Long> tenantRPIMap) {
    tenantRPIMapProp.setValue(
        BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(tenantRPIMap)));
    return this;
  }

  public DirectBuffer getTenantEDIMapValue() {
    return tenantEDIMapProp.getValue();
  }

  public Map<String, Long> getTenantEDIMap() {
    return MsgPackConverter.convertToLongMap(tenantEDIMapProp.getValue());
  }

  public PersistedUsageMetricsV1 setTenantEDIMap(final Map<String, Long> tenantEDIMap) {
    tenantEDIMapProp.setValue(
        BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(tenantEDIMap)));
    return this;
  }

  public DirectBuffer getTenantTUMapValue() {
    return tenantTUMapProp.getValue();
  }

  public Map<String, Set<Long>> getTenantTUMap() {
    return MsgPackConverter.convertToSetLongMap(tenantTUMapProp.getValue());
  }

  public PersistedUsageMetricsV1 setTenantTUMap(final Map<String, Set<Long>> tenantTUMap) {
    tenantTUMapProp.setValue(BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(tenantTUMap)));
    return this;
  }

  public PersistedUsageMetricsV1 recordRPI(final String tenantId) {
    final var tenantRPIMap = getTenantRPIMap();
    tenantRPIMap.merge(tenantId, 1L, Long::sum);
    setTenantRPIMap(tenantRPIMap);
    return this;
  }

  public PersistedUsageMetricsV1 recordEDI(final String tenantId) {
    final var tenantEDIMap = getTenantEDIMap();
    tenantEDIMap.merge(tenantId, 1L, Long::sum);
    setTenantEDIMap(tenantEDIMap);
    return this;
  }

  public PersistedUsageMetricsV1 recordTU(final String tenantId, final Long assignee) {
    final var tenantTUMap = getTenantTUMap();
    final boolean added = tenantTUMap.computeIfAbsent(tenantId, k -> new HashSet<>()).add(assignee);
    if (added) {
      setTenantTUMap(tenantTUMap);
    }
    return this;
  }

  public PersistedUsageMetricsV1 close(final long time) {
    final var bucket = new PersistedUsageMetricsV1();
    bucket.copyFrom(this);
    // Ensure the fromTime is set. This can happen if metrics were recorded before the first
    // applier.
    if (getFromTime() == TIME_NOT_SET) {
      bucket.setFromTime(time);
    }
    bucket.setToTime(time);
    return bucket;
  }
}
