/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.metrics;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;

public class UsageMetricRecord extends UnifiedRecordValue implements UsageMetricRecordValue {

  private final EnumProperty<IntervalType> intervalTypeProp =
      new EnumProperty<>("intervalType", IntervalType.class, IntervalType.ACTIVE);
  private final EnumProperty<EventType> eventTypeProp =
      new EnumProperty<>("eventType", EventType.class, EventType.NONE);
  private final LongProperty resetTimeProp = new LongProperty("resetTime", -1);
  private final LongProperty startTimeProp = new LongProperty("startTime", -1);
  private final LongProperty endTimeProp = new LongProperty("endTime", -1);
  private final DocumentProperty counterValuesProp = new DocumentProperty("counterValues");
  private final DocumentProperty setValuesProp = new DocumentProperty("setValues");

  public UsageMetricRecord() {
    super(7);
    declareProperty(intervalTypeProp)
        .declareProperty(resetTimeProp)
        .declareProperty(startTimeProp)
        .declareProperty(endTimeProp)
        .declareProperty(eventTypeProp)
        .declareProperty(counterValuesProp)
        .declareProperty(setValuesProp);
  }

  public static UsageMetricRecord copyWithoutValues(final UsageMetricRecord usageMetricRecord) {
    return new UsageMetricRecord()
        .setEventType(usageMetricRecord.getEventType())
        .setIntervalType(usageMetricRecord.getIntervalType())
        .setResetTime(usageMetricRecord.getResetTime())
        .setStartTime(usageMetricRecord.getStartTime())
        .setEndTime(usageMetricRecord.getEndTime());
  }

  @Override
  public IntervalType getIntervalType() {
    return intervalTypeProp.getValue();
  }

  public UsageMetricRecord setIntervalType(final IntervalType intervalType) {
    intervalTypeProp.setValue(intervalType);
    return this;
  }

  @Override
  public EventType getEventType() {
    return eventTypeProp.getValue();
  }

  public UsageMetricRecord setEventType(final EventType event) {
    eventTypeProp.setValue(event);
    return this;
  }

  @Override
  public long getStartTime() {
    return startTimeProp.getValue();
  }

  public UsageMetricRecord setStartTime(final Long startTime) {
    startTimeProp.setValue(startTime);
    return this;
  }

  @Override
  public long getEndTime() {
    return endTimeProp.getValue();
  }

  public UsageMetricRecord setEndTime(final Long endTime) {
    endTimeProp.setValue(endTime);
    return this;
  }

  @Override
  public long getResetTime() {
    return resetTimeProp.getValue();
  }

  public UsageMetricRecord setResetTime(final Long resetTime) {
    resetTimeProp.setValue(resetTime);
    return this;
  }

  @Override
  public Map<String, Long> getCounterValues() {
    return MsgPackConverter.convertToLongMap(counterValuesProp.getValue());
  }

  public UsageMetricRecord setCounterValues(final DirectBuffer value) {
    counterValuesProp.setValue(value);
    return this;
  }

  @Override
  public Map<String, Set<String>> getSetValues() {
    return MsgPackConverter.convertToSetStringMap(setValuesProp.getValue());
  }

  public UsageMetricRecord setSetValues(final DirectBuffer value) {
    setValuesProp.setValue(value);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getCounterValueBuffer() {
    return counterValuesProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getSetValueBuffer() {
    return setValuesProp.getValue();
  }
}
