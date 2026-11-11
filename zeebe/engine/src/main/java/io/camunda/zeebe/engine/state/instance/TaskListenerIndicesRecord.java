/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;

/**
 * Plain data; the msgpack codec is generated, see {@link MsgpackPojo}. The field names and their
 * order are the stored format.
 */
@MsgpackPojo
public final class TaskListenerIndicesRecord extends MsgpackBufferValue<TaskListenerIndicesRecord>
    implements DbValue {

  int creatingTaskListenerIndex;
  int assigningTaskListenerIndex;
  int updatingTaskListenerIndex;
  int completingTaskListenerIndex;
  int cancelingTaskListenerIndex;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  TaskListenerIndicesRecord() {
    reset();
  }

  public int getTaskListenerIndex(final ZeebeTaskListenerEventType eventType) {
    return switch (eventType) {
      case creating -> creatingTaskListenerIndex;
      case assigning -> assigningTaskListenerIndex;
      case updating -> updatingTaskListenerIndex;
      case completing -> completingTaskListenerIndex;
      case canceling -> cancelingTaskListenerIndex;
      default -> throw unsupported(eventType);
    };
  }

  public void incrementTaskListenerIndex(final ZeebeTaskListenerEventType eventType) {
    setTaskListenerIndex(eventType, getTaskListenerIndex(eventType) + 1);
  }

  public void resetTaskListenerIndex(final ZeebeTaskListenerEventType eventType) {
    setTaskListenerIndex(eventType, 0);
  }

  /** The only place with the default of every property. */
  @Override
  public void reset() {
    creatingTaskListenerIndex = 0;
    assigningTaskListenerIndex = 0;
    updatingTaskListenerIndex = 0;
    completingTaskListenerIndex = 0;
    cancelingTaskListenerIndex = 0;
    unknownProperties.clear();
  }

  @Override
  protected MsgpackCodec<TaskListenerIndicesRecord> codec() {
    return TaskListenerIndicesRecordCodec.INSTANCE;
  }

  private void setTaskListenerIndex(final ZeebeTaskListenerEventType eventType, final int value) {
    switch (eventType) {
      case creating -> creatingTaskListenerIndex = value;
      case assigning -> assigningTaskListenerIndex = value;
      case updating -> updatingTaskListenerIndex = value;
      case completing -> completingTaskListenerIndex = value;
      case canceling -> cancelingTaskListenerIndex = value;
      default -> throw unsupported(eventType);
    }
  }

  private static IllegalArgumentException unsupported(final ZeebeTaskListenerEventType eventType) {
    return new IllegalArgumentException("Unsupported ZeebeTaskListenerEventType " + eventType);
  }
}
