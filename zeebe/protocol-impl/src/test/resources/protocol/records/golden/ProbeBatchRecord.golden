/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.probe;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ProbeBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ProbeRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.StreamSupport;
import org.agrona.concurrent.UnsafeBuffer;

public final class ProbeBatchRecord extends UnifiedRecordValue implements ProbeBatchRecordValue {

  private final StringProperty probeIdProp = new StringProperty("probeId", "");
  private final StringProperty workerProp = new StringProperty("worker", "");
  private final IntegerProperty maxProbesToActivateProp =
      new IntegerProperty("maxProbesToActivate", -1);
  private final ArrayProperty<StringValue> tenantIdsProp =
      new ArrayProperty<>("tenantIds", StringValue::new);
  private final EnumProperty<TenantFilter> tenantFilterProp =
      new EnumProperty<>("tenantFilter", TenantFilter.class, TenantFilter.PROVIDED);
  private final ArrayProperty<LongValue> probeKeysProp =
      new ArrayProperty<>("probeKeys", LongValue::new);
  private final ArrayProperty<ProbeRecord> probesProp =
      new ArrayProperty<>("probes", ProbeRecord::new);
  private final BooleanProperty truncatedProp = new BooleanProperty("truncated", false);

  public ProbeBatchRecord() {
    super(8);
    declareProperty(probeIdProp)
        .declareProperty(workerProp)
        .declareProperty(maxProbesToActivateProp)
        .declareProperty(tenantIdsProp)
        .declareProperty(tenantFilterProp)
        .declareProperty(probeKeysProp)
        .declareProperty(probesProp)
        .declareProperty(truncatedProp);
  }

  /** Replaces this record's content with a copy of the other record's content. */
  public void copyFrom(final ProbeBatchRecord other) {
    final var bytes = new byte[other.getLength()];
    final var buffer = new UnsafeBuffer(bytes);
    other.write(buffer, 0);
    wrap(buffer, 0, bytes.length);
  }

  @Override
  public String getProbeId() {
    return BufferUtil.bufferAsString(probeIdProp.getValue());
  }

  public ProbeBatchRecord setProbeId(final String probeId) {
    probeIdProp.setValue(probeId);
    return this;
  }

  @Override
  public String getWorker() {
    return BufferUtil.bufferAsString(workerProp.getValue());
  }

  public ProbeBatchRecord setWorker(final String worker) {
    workerProp.setValue(worker);
    return this;
  }

  @Override
  public int getMaxProbesToActivate() {
    return maxProbesToActivateProp.getValue();
  }

  public ProbeBatchRecord setMaxProbesToActivate(final int maxProbesToActivate) {
    maxProbesToActivateProp.setValue(maxProbesToActivate);
    return this;
  }

  @Override
  public List<String> getTenantIds() {
    return StreamSupport.stream(tenantIdsProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .toList();
  }

  public ProbeBatchRecord setTenantIds(final List<String> tenantIds) {
    tenantIdsProp.reset();
    tenantIds.forEach(tenantId -> tenantIdsProp.add().wrap(BufferUtil.wrapString(tenantId)));
    return this;
  }

  @Override
  public TenantFilter getTenantFilter() {
    return tenantFilterProp.getValue();
  }

  public ProbeBatchRecord setTenantFilter(final TenantFilter tenantFilter) {
    tenantFilterProp.setValue(tenantFilter);
    return this;
  }

  @Override
  public List<Long> getProbeKeys() {
    return StreamSupport.stream(probeKeysProp.spliterator(), false)
        .map(LongValue::getValue)
        .toList();
  }

  @Override
  public List<ProbeRecordValue> getProbes() {
    return StreamSupport.stream(probesProp.spliterator(), false)
        .map(probe -> (ProbeRecordValue) probe.copy())
        .toList();
  }

  /** Adds a collected probe; the record is copied, so the caller may reuse its instance. */
  public ProbeBatchRecord addProbe(final long probeKey, final ProbeRecord probe) {
    probeKeysProp.add().setValue(probeKey);
    probesProp.add().copyFrom(probe);
    return this;
  }

  @JsonIgnore
  public ValueArray<LongValue> probeKeys() {
    return probeKeysProp;
  }

  @JsonIgnore
  public ValueArray<ProbeRecord> probes() {
    return probesProp;
  }

  @Override
  public boolean isTruncated() {
    return truncatedProp.getValue();
  }

  public ProbeBatchRecord setTruncated(final boolean truncated) {
    truncatedProp.setValue(truncated);
    return this;
  }
}
