/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.globallistener;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class GlobalListenerRecord extends MsgpackBufferValue<GlobalListenerRecord>
    implements MsgpackRecordValue, GlobalListenerRecordValue {

  public static final int DEFAULT_RETRIES = 3;
  public static final int DEFAULT_PRIORITY = 50;

  /**
   * Comparator to sort global listeners by priority in descending order, and then by id in
   * ascending order. Used when listing global listeners to ensure a deterministic order, and to
   * execute listeners in the correct order based on their priority.
   */
  public static final Comparator<GlobalListenerRecord> PRIORITY_COMPARATOR =
      Comparator.comparingInt(GlobalListenerRecord::getPriority)
          .reversed()
          .thenComparing(GlobalListenerRecord::getId);

  public static final String ALL_EVENT_TYPES = "all";

  long globalListenerKey = -1L;
  DirectBuffer id = Decoding.EMPTY;
  DirectBuffer type = Decoding.EMPTY;
  int retries = DEFAULT_RETRIES;
  final List<DirectBuffer> eventTypes = new ArrayList<>();
  boolean afterNonGlobal = false;
  int priority = DEFAULT_PRIORITY;
  GlobalListenerSource source = DEFAULT_SOURCE;
  GlobalListenerType listenerType = DEFAULT_LISTENER_TYPE;
  long configKey = -1L;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public Long getGlobalListenerKey() {
    return globalListenerKey;
  }

  public GlobalListenerRecord setGlobalListenerKey(final Long key) {
    globalListenerKey = key;
    return this;
  }

  @Override
  public String getId() {
    return bufferAsString(id);
  }

  public GlobalListenerRecord setId(final String id) {
    this.id = BufferUtil.wrapString(id);
    return this;
  }

  @Override
  public String getType() {
    return bufferAsString(type);
  }

  public GlobalListenerRecord setType(final String type) {
    this.type = BufferUtil.wrapString(type);
    return this;
  }

  @Override
  public int getRetries() {
    return retries;
  }

  public GlobalListenerRecord setRetries(final int retries) {
    this.retries = retries;
    return this;
  }

  @Override
  public List<String> getEventTypes() {
    return eventTypes.stream().map(BufferUtil::bufferAsString).collect(Collectors.toList());
  }

  public GlobalListenerRecord setEventTypes(final List<String> eventTypes) {
    this.eventTypes.clear();
    eventTypes.forEach(this::addEventType);
    return this;
  }

  @Override
  public boolean isAfterNonGlobal() {
    return afterNonGlobal;
  }

  public GlobalListenerRecord setAfterNonGlobal(final boolean afterNonGlobal) {
    this.afterNonGlobal = afterNonGlobal;
    return this;
  }

  @Override
  public int getPriority() {
    return priority;
  }

  public GlobalListenerRecord setPriority(final int priority) {
    this.priority = priority;
    return this;
  }

  @Override
  public GlobalListenerSource getSource() {
    return source;
  }

  public GlobalListenerRecord setSource(final GlobalListenerSource source) {
    this.source = source;
    return this;
  }

  @Override
  public GlobalListenerType getListenerType() {
    return listenerType;
  }

  public GlobalListenerRecord setListenerType(final GlobalListenerType listenerType) {
    this.listenerType = listenerType;
    return this;
  }

  @Override
  public Long getConfigKey() {
    return configKey;
  }

  public GlobalListenerRecord setConfigKey(final Long configKey) {
    this.configKey = configKey;
    return this;
  }

  public GlobalListenerRecord addEventType(final String eventType) {
    eventTypes.add(BufferUtil.wrapString(eventType));
    return this;
  }

  @Override
  protected MsgpackCodec<GlobalListenerRecord> codec() {
    return GlobalListenerRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<GlobalListenerRecord> ops() {
    return GlobalListenerRecordOps.INSTANCE;
  }
}
