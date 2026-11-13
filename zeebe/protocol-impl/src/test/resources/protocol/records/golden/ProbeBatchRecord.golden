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
import java.util.Objects;
import java.util.stream.StreamSupport;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ProbeBatchRecord extends UnifiedRecordValue implements ProbeBatchRecordValue {

  // Static StringValue keys to avoid memory waste
  private static final StringValue PROBE_IDS_KEY = new StringValue("probeIds");
  private static final StringValue WORKER_KEY = new StringValue("worker");
  private static final StringValue MAX_PROBES_TO_ACTIVATE_KEY =
      new StringValue("maxProbesToActivate");
  private static final StringValue PROBE_KEYS_KEY = new StringValue("probeKeys");
  private static final StringValue PROBES_KEY = new StringValue("probes");
  private static final StringValue TRUNCATED_KEY = new StringValue("truncated");
  private static final StringValue TENANT_IDS_KEY = new StringValue("tenantIds");
  private static final StringValue TENANT_FILTER_KEY = new StringValue("tenantFilter");

  private final ArrayProperty<StringValue> probeIdsProp =
      new ArrayProperty<>(PROBE_IDS_KEY, StringValue::new);
  private final StringProperty workerProp = new StringProperty(WORKER_KEY, "");
  private final IntegerProperty maxProbesToActivateProp =
      new IntegerProperty(MAX_PROBES_TO_ACTIVATE_KEY, -1);
  private final ArrayProperty<LongValue> probeKeysProp =
      new ArrayProperty<>(PROBE_KEYS_KEY, LongValue::new);
  private final ArrayProperty<ProbeRecord> probesProp =
      new ArrayProperty<>(PROBES_KEY, ProbeRecord::new);
  private final BooleanProperty truncatedProp = new BooleanProperty(TRUNCATED_KEY, false);
  private final ArrayProperty<StringValue> tenantIdsProp =
      new ArrayProperty<>(TENANT_IDS_KEY, StringValue::new);
  private final EnumProperty<TenantFilter> tenantFilterProp =
      new EnumProperty<>(TENANT_FILTER_KEY, TenantFilter.class, TenantFilter.PROVIDED);

  public ProbeBatchRecord() {
    super(8);
    declareProperty(probeIdsProp)
        .declareProperty(workerProp)
        .declareProperty(maxProbesToActivateProp)
        .declareProperty(probeKeysProp)
        .declareProperty(probesProp)
        .declareProperty(truncatedProp)
        .declareProperty(tenantIdsProp)
        .declareProperty(tenantFilterProp);
  }

  @Override
  public List<String> getProbeIds() {
    return StreamSupport.stream(probeIdsProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .toList();
  }

  public ProbeBatchRecord setProbeIds(final List<String> probeIds) {
    probeIdsProp.reset();
    probeIds.forEach(probeId -> probeIdsProp.add().wrap(BufferUtil.wrapString(probeId)));
    return this;
  }

  /**
   * Direct access to the probe ID elements. The elements stay owned by this record; do not hold on
   * to them.
   */
  @JsonIgnore
  public ValueArray<StringValue> probeIds() {
    return probeIdsProp;
  }

  @Override
  public String getWorker() {
    return BufferUtil.bufferAsString(workerProp.getValue());
  }

  public ProbeBatchRecord setWorker(final String worker) {
    workerProp.setValue(worker);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getWorkerBuffer() {
    return workerProp.getValue();
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
  public List<Long> getProbeKeys() {
    return StreamSupport.stream(probeKeysProp.spliterator(), false)
        .map(LongValue::getValue)
        .toList();
  }

  @JsonIgnore
  public ValueArray<LongValue> probeKeys() {
    return probeKeysProp;
  }

  @Override
  public List<ProbeRecordValue> getProbes() {
    // detach copies so the returned list stays valid if this record is reused or reset later
    return StreamSupport.stream(probesProp.spliterator(), false)
        .map(probe -> (ProbeRecordValue) new ProbeRecord().copyFrom(probe))
        .toList();
  }

  @JsonIgnore
  public ValueArray<ProbeRecord> probes() {
    return probesProp;
  }

  /** Adds a collected probe; its record is copied, so the caller may reuse its instance. */
  public ProbeBatchRecord addProbe(final long probeKey, final ProbeRecord probe) {
    probeKeysProp.add().setValue(probeKey);
    probesProp.add().copyFrom(probe);
    return this;
  }

  @Override
  public boolean isTruncated() {
    return truncatedProp.getValue();
  }

  public ProbeBatchRecord setTruncated(final boolean truncated) {
    truncatedProp.setValue(truncated);
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
    // never null: the property has a default
    return Objects.requireNonNull(tenantFilterProp.getValue());
  }

  public ProbeBatchRecord setTenantFilter(final TenantFilter tenantFilter) {
    tenantFilterProp.setValue(tenantFilter);
    return this;
  }
}
