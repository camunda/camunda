/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.globallistener;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.agrona.collections.LongArrayList;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class GlobalListenerBatchRecord extends MsgpackBufferValue<GlobalListenerBatchRecord>
    implements MsgpackRecordValue, GlobalListenerBatchRecordValue {

  long globalListenerBatchKey = -1L;
  final List<GlobalListenerRecord> listeners = new ArrayList<>();

  // Metadata to apply state mutations on command distribution
  final LongArrayList createdListenerKeys = new LongArrayList();
  final LongArrayList updatedListenerKeys = new LongArrayList();
  final LongArrayList deletedListenerKeys = new LongArrayList();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getGlobalListenerBatchKey() {
    return globalListenerBatchKey;
  }

  public GlobalListenerBatchRecord setGlobalListenerBatchKey(final long globalListenerBatchKey) {
    this.globalListenerBatchKey = globalListenerBatchKey;
    return this;
  }

  @Override
  public List<GlobalListenerRecordValue> getListeners() {
    return listeners.stream().collect(Collectors.toList());
  }

  @Override
  public Set<Long> getCreatedListenerKeys() {
    return createdListenerKeys.stream().collect(Collectors.toSet());
  }

  @Override
  public Set<Long> getUpdatedListenerKeys() {
    return updatedListenerKeys.stream().collect(Collectors.toSet());
  }

  @Override
  public Set<Long> getDeletedListenerKeys() {
    return deletedListenerKeys.stream().collect(Collectors.toSet());
  }

  @JsonIgnore
  public List<GlobalListenerRecord> getTaskListeners() {
    return listeners.stream()
        .filter(listener -> listener.getListenerType() == GlobalListenerType.USER_TASK)
        .toList();
  }

  /**
   * Check if two global listener batches lead to equivalent configurations, ignoring the keys of
   * the batch and the listeners.
   */
  public boolean isSameConfiguration(final GlobalListenerBatchRecord other) {
    final UnaryOperator<GlobalListenerRecord> makeComparable =
        listener -> {
          final GlobalListenerRecord copy = new GlobalListenerRecord();
          copy.copyFrom(listener);
          copy.setGlobalListenerKey(-1L); // ignore keys when comparing listeners
          return copy;
        };
    final Set<GlobalListenerRecord> thisListeners =
        listeners.stream().map(makeComparable).collect(Collectors.toSet());
    final Set<GlobalListenerRecord> otherListeners =
        other.listeners.stream().map(makeComparable).collect(Collectors.toSet());
    return thisListeners.equals(otherListeners);
  }

  public GlobalListenerBatchRecord addListener(final GlobalListenerRecord listener) {
    final var copy = new GlobalListenerRecord();
    copy.copyFrom(listener);
    listeners.add(copy);
    return this;
  }

  public GlobalListenerBatchRecord addCreatedListener(final GlobalListenerRecord listener) {
    createdListenerKeys.addLong(listener.getGlobalListenerKey());
    return this;
  }

  public GlobalListenerBatchRecord addUpdatedListener(final GlobalListenerRecord listener) {
    updatedListenerKeys.addLong(listener.getGlobalListenerKey());
    return this;
  }

  public GlobalListenerBatchRecord addDeletedListener(final GlobalListenerRecord listener) {
    deletedListenerKeys.addLong(listener.getGlobalListenerKey());
    return this;
  }

  @Override
  protected MsgpackCodec<GlobalListenerBatchRecord> codec() {
    return GlobalListenerBatchRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<GlobalListenerBatchRecord> ops() {
    return GlobalListenerBatchRecordOps.INSTANCE;
  }
}
