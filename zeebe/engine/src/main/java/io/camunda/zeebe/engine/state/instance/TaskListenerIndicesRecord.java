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
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class TaskListenerIndicesRecord extends MsgpackBufferValue<TaskListenerIndicesRecord>
    implements DbValue {

  int creatingTaskListenerIndex = 0;
  int assigningTaskListenerIndex = 0;
  int updatingTaskListenerIndex = 0;
  int completingTaskListenerIndex = 0;
  int cancelingTaskListenerIndex = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public int getTaskListenerIndex(final ZeebeTaskListenerEventType eventType) {
    return switch (eventType) {
      case creating -> creatingTaskListenerIndex;
      case assigning -> assigningTaskListenerIndex;
      case updating -> updatingTaskListenerIndex;
      case completing -> completingTaskListenerIndex;
      case canceling -> cancelingTaskListenerIndex;
      default ->
          throw new IllegalArgumentException("Unsupported ZeebeTaskListenerEventType " + eventType);
    };
  }

  public void incrementTaskListenerIndex(final ZeebeTaskListenerEventType eventType) {
    switch (eventType) {
      case creating -> creatingTaskListenerIndex++;
      case assigning -> assigningTaskListenerIndex++;
      case updating -> updatingTaskListenerIndex++;
      case completing -> completingTaskListenerIndex++;
      case canceling -> cancelingTaskListenerIndex++;
      default ->
          throw new IllegalArgumentException("Unsupported ZeebeTaskListenerEventType " + eventType);
    }
  }

  public void resetTaskListenerIndex(final ZeebeTaskListenerEventType eventType) {
    switch (eventType) {
      case creating -> creatingTaskListenerIndex = 0;
      case assigning -> assigningTaskListenerIndex = 0;
      case updating -> updatingTaskListenerIndex = 0;
      case completing -> completingTaskListenerIndex = 0;
      case canceling -> cancelingTaskListenerIndex = 0;
      default ->
          throw new IllegalArgumentException("Unsupported ZeebeTaskListenerEventType " + eventType);
    }
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    final byte[] bytes = new byte[length];
    final UnsafeBuffer mutableBuffer = new UnsafeBuffer(bytes);
    buffer.getBytes(offset, bytes, 0, length);
    super.wrap(mutableBuffer, 0, length);
  }

  @Override
  protected MsgpackCodec<TaskListenerIndicesRecord> codec() {
    return TaskListenerIndicesRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<TaskListenerIndicesRecord> ops() {
    return TaskListenerIndicesRecordOps.INSTANCE;
  }
}
