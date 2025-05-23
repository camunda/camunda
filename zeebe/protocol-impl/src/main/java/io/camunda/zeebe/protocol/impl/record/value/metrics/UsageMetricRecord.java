/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.metrics;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;

public class UsageMetricRecord extends UnifiedRecordValue implements UsageMetricRecordValue {

  private final LongProperty startTimeProp = new LongProperty("startTime", -1);
  private final LongProperty endTimeProp = new LongProperty("endTime", -1);
  private final StringProperty eventProp = new StringProperty("event", "");
  private final DocumentProperty valueProp = new DocumentProperty("variables");

  public UsageMetricRecord() {
    super(4);
    declareProperty(startTimeProp)
        .declareProperty(endTimeProp)
        .declareProperty(eventProp)
        .declareProperty(valueProp);
  }

  @Override
  public UsageMetricEvent getEvent() {
    return UsageMetricEvent.from(bufferAsString(eventProp.getValue()));
  }

  public UsageMetricRecord setEvent(final UsageMetricEvent event) {
    eventProp.setValue(event.value());
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
  public Map<String, List<Long>> getValue() {
    return MsgPackConverter.convertToLongListMap(valueProp.getValue());
  }

  public UsageMetricRecord setValue(final DirectBuffer value) {
    valueProp.setValue(value);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getValueBuffer() {
    return valueProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getEventBuffer() {
    return eventProp.getValue();
  }
}
