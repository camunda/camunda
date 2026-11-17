/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.metrics;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class UsageMetricRecord extends MsgpackBufferValue<UsageMetricRecord>
    implements MsgpackRecordValue, UsageMetricRecordValue {

  IntervalType intervalType = IntervalType.ACTIVE;
  long resetTime = -1L;
  long startTime = -1L;
  long endTime = -1L;
  EventType eventType = EventType.NONE;

  @MsgpackProperty(document = true)
  DirectBuffer counterValues = DocumentValue.EMPTY_DOCUMENT;

  @MsgpackProperty(document = true)
  DirectBuffer setValues = DocumentValue.EMPTY_DOCUMENT;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public IntervalType getIntervalType() {
    return intervalType;
  }

  public UsageMetricRecord setIntervalType(final IntervalType intervalType) {
    this.intervalType = intervalType;
    return this;
  }

  @Override
  public EventType getEventType() {
    return eventType;
  }

  public UsageMetricRecord setEventType(final EventType event) {
    eventType = event;
    return this;
  }

  @Override
  public long getStartTime() {
    return startTime;
  }

  public UsageMetricRecord setStartTime(final Long startTime) {
    this.startTime = startTime;
    return this;
  }

  @Override
  public long getEndTime() {
    return endTime;
  }

  public UsageMetricRecord setEndTime(final Long endTime) {
    this.endTime = endTime;
    return this;
  }

  @Override
  public long getResetTime() {
    return resetTime;
  }

  public UsageMetricRecord setResetTime(final Long resetTime) {
    this.resetTime = resetTime;
    return this;
  }

  @Override
  public Map<String, Long> getCounterValues() {
    return MsgPackConverter.convertToLongMap(counterValues);
  }

  public UsageMetricRecord setCounterValues(final DirectBuffer value) {
    counterValues = Decoding.document(value);
    return this;
  }

  @Override
  public Map<String, Set<Long>> getSetValues() {
    return MsgPackConverter.convertToSetLongMap(setValues);
  }

  public UsageMetricRecord setSetValues(final DirectBuffer value) {
    setValues = Decoding.document(value);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getCounterValueBuffer() {
    return counterValues;
  }

  @JsonIgnore
  public DirectBuffer getSetValueBuffer() {
    return setValues;
  }

  public boolean hasData() {
    return !(getCounterValues().isEmpty() && getSetValues().isEmpty());
  }

  @Override
  protected MsgpackCodec<UsageMetricRecord> codec() {
    return UsageMetricRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<UsageMetricRecord> ops() {
    return UsageMetricRecordOps.INSTANCE;
  }
}
