/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.metrics;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class PersistedUsageMetrics extends MsgpackBufferValue<PersistedUsageMetrics>
    implements DbValue {

  public static final int TIME_NOT_SET = -1;

  long fromTime = TIME_NOT_SET;
  long toTime = TIME_NOT_SET;

  @MsgpackProperty(document = true)
  DirectBuffer tenantRPI = DocumentValue.EMPTY_DOCUMENT;

  @MsgpackProperty(document = true)
  DirectBuffer tenantEDI = DocumentValue.EMPTY_DOCUMENT;

  @MsgpackProperty(document = true)
  DirectBuffer tenantTU = DocumentValue.EMPTY_DOCUMENT;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public long getFromTime() {
    return fromTime;
  }

  public PersistedUsageMetrics setFromTime(final long fromTime) {
    this.fromTime = fromTime;
    return this;
  }

  public long getToTime() {
    return toTime;
  }

  public PersistedUsageMetrics setToTime(final long toTime) {
    this.toTime = toTime;
    return this;
  }

  public DirectBuffer getTenantRPIMapValue() {
    return tenantRPI;
  }

  public Map<String, Long> getTenantRPIMap() {
    return MsgPackConverter.convertToLongMap(getTenantRPIMapValue());
  }

  public PersistedUsageMetrics setTenantRPIMap(final Map<String, Long> tenantRPIMap) {
    tenantRPI =
        Decoding.document(BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(tenantRPIMap)));
    return this;
  }

  public DirectBuffer getTenantEDIMapValue() {
    return tenantEDI;
  }

  public Map<String, Long> getTenantEDIMap() {
    return MsgPackConverter.convertToLongMap(getTenantEDIMapValue());
  }

  public PersistedUsageMetrics setTenantEDIMap(final Map<String, Long> tenantEDIMap) {
    tenantEDI =
        Decoding.document(BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(tenantEDIMap)));
    return this;
  }

  public DirectBuffer getTenantTUMapValue() {
    return tenantTU;
  }

  public Map<String, Set<Long>> getTenantTUMap() {
    return MsgPackConverter.convertToSetLongMap(getTenantTUMapValue());
  }

  public PersistedUsageMetrics setTenantTUMap(final Map<String, Set<Long>> tenantTUMap) {
    tenantTU =
        Decoding.document(BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(tenantTUMap)));
    return this;
  }

  public PersistedUsageMetrics recordRPI(final String tenantId) {
    final var tenantRPIMap = getTenantRPIMap();
    tenantRPIMap.merge(tenantId, 1L, Long::sum);
    setTenantRPIMap(tenantRPIMap);
    return this;
  }

  public PersistedUsageMetrics recordEDI(final String tenantId) {
    final var tenantEDIMap = getTenantEDIMap();
    tenantEDIMap.merge(tenantId, 1L, Long::sum);
    setTenantEDIMap(tenantEDIMap);
    return this;
  }

  public PersistedUsageMetrics recordTU(final String tenantId, final Long assignee) {
    final var tenantTUMap = getTenantTUMap();
    final boolean added = tenantTUMap.computeIfAbsent(tenantId, k -> new HashSet<>()).add(assignee);
    if (added) {
      setTenantTUMap(tenantTUMap);
    }
    return this;
  }

  public PersistedUsageMetrics close(final long time) {
    final var bucket = new PersistedUsageMetrics();
    bucket.copyFrom(this);
    // Ensure the fromTime is set. This can happen if metrics were recorded before the first
    // applier.
    if (getFromTime() == TIME_NOT_SET) {
      bucket.setFromTime(time);
    }
    bucket.setToTime(time);
    return bucket;
  }

  @Override
  protected MsgpackCodec<PersistedUsageMetrics> codec() {
    return PersistedUsageMetricsCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedUsageMetrics> ops() {
    return PersistedUsageMetricsOps.INSTANCE;
  }
}
