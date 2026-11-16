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
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class GlobalListenerRecordV1 extends UnifiedRecordValue
    implements GlobalListenerRecordValue {

  public static final int DEFAULT_RETRIES = 3;
  public static final int DEFAULT_PRIORITY = 50;

  /**
   * Comparator to sort global listeners by priority in descending order, and then by id in
   * ascending order. Used when listing global listeners to ensure a deterministic order, and to
   * execute listeners in the correct order based on their priority.
   */
  public static final Comparator<GlobalListenerRecordV1> PRIORITY_COMPARATOR =
      Comparator.comparingInt(GlobalListenerRecordV1::getPriority)
          .reversed()
          .thenComparing(GlobalListenerRecordV1::getId);

  public static final String ALL_EVENT_TYPES = "all";

  private final LongProperty globalListenerKeyProp = new LongProperty("globalListenerKey", -1L);
  private final StringProperty idProp = new StringProperty("id", "");
  private final StringProperty typeProp = new StringProperty("type", "");
  private final IntegerProperty retriesProp = new IntegerProperty("retries", DEFAULT_RETRIES);
  private final ArrayProperty<StringValue> eventTypesProp =
      new ArrayProperty<>("eventTypes", StringValue::new);
  private final BooleanProperty afterNonGlobalProp = new BooleanProperty("afterNonGlobal", false);
  private final IntegerProperty priorityProp = new IntegerProperty("priority", DEFAULT_PRIORITY);
  private final EnumProperty<GlobalListenerSource> sourceProp =
      new EnumProperty<>("source", GlobalListenerSource.class, DEFAULT_SOURCE);
  private final EnumProperty<GlobalListenerType> listenerTypeProp =
      new EnumProperty<>("listenerType", GlobalListenerType.class, DEFAULT_LISTENER_TYPE);

  private final LongProperty configKeyProp = new LongProperty("configKey", -1L);

  public GlobalListenerRecordV1() {
    super(10);
    declareProperty(globalListenerKeyProp)
        .declareProperty(idProp)
        .declareProperty(typeProp)
        .declareProperty(retriesProp)
        .declareProperty(eventTypesProp)
        .declareProperty(afterNonGlobalProp)
        .declareProperty(priorityProp)
        .declareProperty(sourceProp)
        .declareProperty(listenerTypeProp)
        .declareProperty(configKeyProp);
  }

  @Override
  public Long getGlobalListenerKey() {
    return globalListenerKeyProp.getValue();
  }

  public GlobalListenerRecordV1 setGlobalListenerKey(final Long key) {
    globalListenerKeyProp.setValue(key);
    return this;
  }

  @Override
  public String getId() {
    return bufferAsString(idProp.getValue());
  }

  public GlobalListenerRecordV1 setId(final String id) {
    idProp.setValue(id);
    return this;
  }

  @Override
  public String getType() {
    return bufferAsString(typeProp.getValue());
  }

  public GlobalListenerRecordV1 setType(final String type) {
    typeProp.setValue(type);
    return this;
  }

  @Override
  public int getRetries() {
    return retriesProp.getValue();
  }

  public GlobalListenerRecordV1 setRetries(final int retries) {
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

  public GlobalListenerRecordV1 setEventTypes(final List<String> eventTypes) {
    eventTypesProp.reset();
    eventTypes.forEach(this::addEventType);
    return this;
  }

  @Override
  public boolean isAfterNonGlobal() {
    return afterNonGlobalProp.getValue();
  }

  public GlobalListenerRecordV1 setAfterNonGlobal(final boolean afterNonGlobal) {
    afterNonGlobalProp.setValue(afterNonGlobal);
    return this;
  }

  @Override
  public int getPriority() {
    return priorityProp.getValue();
  }

  public GlobalListenerRecordV1 setPriority(final int priority) {
    priorityProp.setValue(priority);
    return this;
  }

  @Override
  public GlobalListenerSource getSource() {
    return sourceProp.getValue();
  }

  public GlobalListenerRecordV1 setSource(final GlobalListenerSource source) {
    sourceProp.setValue(source);
    return this;
  }

  @Override
  public GlobalListenerType getListenerType() {
    return listenerTypeProp.getValue();
  }

  public GlobalListenerRecordV1 setListenerType(final GlobalListenerType listenerType) {
    listenerTypeProp.setValue(listenerType);
    return this;
  }

  @Override
  public Long getConfigKey() {
    return configKeyProp.getValue();
  }

  public GlobalListenerRecordV1 setConfigKey(final Long configKey) {
    configKeyProp.setValue(configKey);
    return this;
  }

  public GlobalListenerRecordV1 addEventType(final String eventType) {
    eventTypesProp.add().wrap(BufferUtil.wrapString(eventType));
    return this;
  }
}
