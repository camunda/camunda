/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.probe;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.ProbeBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ProbeRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.agrona.collections.LongArrayList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class ProbeBatchRecord extends MsgpackBufferValue<ProbeBatchRecord>
    implements MsgpackRecordValue, ProbeBatchRecordValue {

  final List<DirectBuffer> probeIds = new ArrayList<>();
  DirectBuffer worker = Decoding.EMPTY;
  int maxProbesToActivate = -1;
  final LongArrayList probeKeys = new LongArrayList();
  final List<ProbeRecord> probes = new ArrayList<>();
  boolean truncated = false;
  final List<DirectBuffer> tenantIds = new ArrayList<>();
  TenantFilter tenantFilter = TenantFilter.PROVIDED;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public List<String> getProbeIds() {
    return probeIds.stream().map(BufferUtil::bufferAsString).toList();
  }

  public ProbeBatchRecord setProbeIds(final List<String> probeIds) {
    this.probeIds.clear();
    probeIds.forEach(probeId -> this.probeIds.add(BufferUtil.wrapString(probeId)));
    return this;
  }

  /**
   * Read-only view of the probe ID elements, taken when called: adding to it is not supported, use
   * {@link #setProbeIds(List)}.
   */
  @JsonIgnore
  public ValueArray<StringValue> probeIds() {
    return new ListValueArray<>(probeIds.stream().map(StringValue::new).toList(), null);
  }

  @Override
  public String getWorker() {
    return BufferUtil.bufferAsString(worker);
  }

  public ProbeBatchRecord setWorker(final String worker) {
    this.worker = BufferUtil.wrapString(worker);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getWorkerBuffer() {
    return worker;
  }

  @Override
  public int getMaxProbesToActivate() {
    return maxProbesToActivate;
  }

  public ProbeBatchRecord setMaxProbesToActivate(final int maxProbesToActivate) {
    this.maxProbesToActivate = maxProbesToActivate;
    return this;
  }

  @Override
  public List<Long> getProbeKeys() {
    return new ArrayList<>(probeKeys);
  }

  /**
   * Read-only view of the probe key elements, taken when called: adding to it is not supported, use
   * {@link #addProbe(long, ProbeRecord)}.
   */
  @JsonIgnore
  public ValueArray<LongValue> probeKeys() {
    return new ListValueArray<>(probeKeys.stream().map(LongValue::new).toList(), null);
  }

  @Override
  public List<ProbeRecordValue> getProbes() {
    // detach copies so the returned list stays valid if this record is reused or reset later
    return probes.stream()
        .map(
            probe -> {
              final var copy = new ProbeRecord();
              copy.copyFrom(probe);
              return (ProbeRecordValue) copy;
            })
        .toList();
  }

  @JsonIgnore
  public ValueArray<ProbeRecord> probes() {
    return new ListValueArray<>(probes, ProbeRecord::new);
  }

  /** Adds a collected probe; its record is copied, so the caller may reuse its instance. */
  public ProbeBatchRecord addProbe(final long probeKey, final ProbeRecord probe) {
    probeKeys.addLong(probeKey);
    final var copy = new ProbeRecord();
    copy.copyFrom(probe);
    probes.add(copy);
    return this;
  }

  @Override
  public boolean isTruncated() {
    return truncated;
  }

  public ProbeBatchRecord setTruncated(final boolean truncated) {
    this.truncated = truncated;
    return this;
  }

  @Override
  public List<String> getTenantIds() {
    return tenantIds.stream().map(BufferUtil::bufferAsString).toList();
  }

  public ProbeBatchRecord setTenantIds(final List<String> tenantIds) {
    this.tenantIds.clear();
    tenantIds.forEach(tenantId -> this.tenantIds.add(BufferUtil.wrapString(tenantId)));
    return this;
  }

  @Override
  public TenantFilter getTenantFilter() {
    return tenantFilter;
  }

  public ProbeBatchRecord setTenantFilter(final TenantFilter tenantFilter) {
    this.tenantFilter = tenantFilter;
    return this;
  }

  @Override
  protected MsgpackCodec<ProbeBatchRecord> codec() {
    return ProbeBatchRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProbeBatchRecord> ops() {
    return ProbeBatchRecordOps.INSTANCE;
  }

  private static final class ListValueArray<T> implements ValueArray<T> {

    final List<T> elements;
    @Nullable final Supplier<T> factory;

    private ListValueArray(final List<T> elements, @Nullable final Supplier<T> factory) {
      this.elements = elements;
      this.factory = factory;
    }

    @Override
    public T add() {
      return add(elements.size());
    }

    @Override
    public T add(final int index) {
      if (factory == null) {
        throw new UnsupportedOperationException("This view is read-only");
      }
      final var element = factory.get();
      elements.add(index, element);
      return element;
    }

    @Override
    public T get(final int index) {
      return elements.get(index);
    }

    @Override
    public T remove(final int index) {
      return elements.remove(index);
    }

    @Override
    public int size() {
      return elements.size();
    }

    @Override
    public Stream<T> stream() {
      return elements.stream();
    }

    @Override
    public Iterator<T> iterator() {
      return elements.iterator();
    }
  }
}
