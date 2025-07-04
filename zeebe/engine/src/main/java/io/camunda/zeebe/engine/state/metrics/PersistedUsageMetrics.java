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
import java.util.Map;
import org.agrona.DirectBuffer;

public class PersistedUsageMetrics extends UnpackedObject implements DbValue {

  private final LongProperty fromTimeProp = new LongProperty("fromTime");
  private final LongProperty toTimeProp = new LongProperty("toTime");
  private final DocumentProperty tenantRPIMapProp = new DocumentProperty("tenantRPI");
  private final DocumentProperty tenantEDIMapProp = new DocumentProperty("tenantEDI");

  public PersistedUsageMetrics() {
    super(4);
    declareProperty(fromTimeProp).declareProperty(toTimeProp).declareProperty(tenantRPIMapProp)
        .declareProperty(tenantEDIMapProp);
  }

  public long getFromTime() {
    return fromTimeProp.getValue();
  }

  public PersistedUsageMetrics setFromTime(final long fromTime) {
    fromTimeProp.setValue(fromTime);
    return this;
  }

  public long getToTime() {
    return toTimeProp.getValue();
  }

  public PersistedUsageMetrics setToTime(final long toTime) {
    toTimeProp.setValue(toTime);
    return this;
  }

  public DirectBuffer getTenantRPIMapValue() {
    return tenantRPIMapProp.getValue();
  }

  public Map<String, Long> getTenantRPIMap() {
    return MsgPackConverter.convertToLongMap(tenantRPIMapProp.getValue());
  }

  public PersistedUsageMetrics setTenantRPIMap(final Map<String, Long> tenantRPIMap) {
    tenantRPIMapProp.setValue(
        BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(tenantRPIMap)));
    return this;
  }

  public DirectBuffer getTenantEDIMapValue() {
    return tenantRPIMapProp.getValue();
  }

  public Map<String, Long> getTenantEDIMap() {
    return MsgPackConverter.convertToLongMap(tenantEDIMapProp.getValue());
  }

  public void setTenantEDIMap(final Map<String, Long> tenantEDIMap) {
    tenantEDIMapProp.setValue(
        BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(tenantEDIMap)));
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
}
