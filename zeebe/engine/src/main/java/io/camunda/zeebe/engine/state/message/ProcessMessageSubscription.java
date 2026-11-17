/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.message;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ProcessMessageSubscription extends MsgpackBufferValue<ProcessMessageSubscription>
    implements DbValue {

  final ProcessMessageSubscriptionRecord record = new ProcessMessageSubscriptionRecord();
  State state = State.STATE_OPENING;
  final RequiredLong key = new RequiredLong();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public ProcessMessageSubscriptionRecord getRecord() {
    return record;
  }

  public ProcessMessageSubscription setRecord(final ProcessMessageSubscriptionRecord record) {
    this.record.wrap(record);
    return this;
  }

  public boolean isOpening() {
    return state == State.STATE_OPENING;
  }

  public ProcessMessageSubscription setOpening() {
    state = State.STATE_OPENING;
    return this;
  }

  public ProcessMessageSubscription setOpened() {
    state = State.STATE_OPENED;
    return this;
  }

  public boolean isClosing() {
    return state == State.STATE_CLOSING;
  }

  public ProcessMessageSubscription setClosing() {
    state = State.STATE_CLOSING;
    return this;
  }

  public long getKey() {
    return key.get();
  }

  public ProcessMessageSubscription setKey(final long key) {
    this.key.set(key);
    return this;
  }

  public State getState() {
    return state;
  }

  public State setState(final State state) {
    this.state = state;
    return state;
  }

  // The nested record reads its values from the input buffer, so the input is copied first.
  @Override
  protected MsgpackCodec<ProcessMessageSubscription> codec() {
    return ProcessMessageSubscriptionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessMessageSubscription> ops() {
    return ProcessMessageSubscriptionOps.INSTANCE;
  }

  public enum State {
    STATE_OPENING,
    STATE_OPENED,
    STATE_CLOSING,
  }
}
