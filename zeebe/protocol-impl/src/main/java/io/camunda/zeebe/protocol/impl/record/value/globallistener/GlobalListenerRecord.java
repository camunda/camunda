/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.globallistener;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class GlobalListenerRecord extends UnifiedRecordValue
    implements GlobalListenerRecordValue {

  public static final int DEFAULT_RETRIES = 3;
  public static final int DEFAULT_PRIORITY = 50;

  private final StringProperty idProp = new StringProperty("id", "");
  private final StringProperty typeProp = new StringProperty("type", "");
  private final IntegerProperty retriesProp = new IntegerProperty("retries", DEFAULT_RETRIES);
  private final ArrayProperty<StringValue> eventTypesProp =
      new ArrayProperty<>("eventTypes", StringValue::new);
  private final BooleanProperty afterNonGlobalProp = new BooleanProperty("afterNonGlobal", false);
  private final IntegerProperty priorityProp = new IntegerProperty("priority", DEFAULT_PRIORITY);
  private final EnumProperty<GlobalListenerSource> sourceProp =
      new EnumProperty<>("source", GlobalListenerSource.class, GlobalListenerSource.CONFIGURATION);

  public GlobalListenerRecord() {
    super(7);
    declareProperty(idProp)
        .declareProperty(typeProp)
        .declareProperty(retriesProp)
        .declareProperty(eventTypesProp)
        .declareProperty(afterNonGlobalProp)
        .declareProperty(priorityProp)
        .declareProperty(sourceProp);
  }

  @Override
  public String getId() {
    return bufferAsString(idProp.getValue());
  }

  public GlobalListenerRecord setId(final String id) {
    idProp.setValue(id);
    return this;
  }

  @Override
  public String getType() {
    return bufferAsString(typeProp.getValue());
  }

  public GlobalListenerRecord setType(final String type) {
    typeProp.setValue(type);
    return this;
  }

  @Override
  public int getRetries() {
    return retriesProp.getValue();
  }

  public GlobalListenerRecord setRetries(final int retries) {
    retriesProp.setValue(retries);
    return this;
  }

  @Override
  public List<String> getEventTypes() {
    return StreamSupport.stream(eventTypesProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .collect(Collectors.toList());
  }

  public GlobalListenerRecord setEventTypes(final List<String> eventTypes) {
    eventTypesProp.reset();
    eventTypes.forEach(this::addEventType);
    return this;
  }

  @Override
  public boolean isAfterNonGlobal() {
    return afterNonGlobalProp.getValue();
  }

  public GlobalListenerRecord setAfterNonGlobal(final boolean afterNonGlobal) {
    afterNonGlobalProp.setValue(afterNonGlobal);
    return this;
  }

  @Override
  public int getPriority() {
    return priorityProp.getValue();
  }

  public GlobalListenerRecord setPriority(final int priority) {
    priorityProp.setValue(priority);
    return this;
  }

  @Override
  public GlobalListenerSource getSource() {
    return sourceProp.getValue();
  }

  public GlobalListenerRecord setSource(final GlobalListenerSource source) {
    sourceProp.setValue(source);
    return this;
  }

  public GlobalListenerRecord addEventType(final String eventType) {
    eventTypesProp.add().wrap(BufferUtil.wrapString(eventType));
    return this;
  }
}
