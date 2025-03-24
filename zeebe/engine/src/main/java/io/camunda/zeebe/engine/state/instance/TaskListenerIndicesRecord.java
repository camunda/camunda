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
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class TaskListenerIndicesRecord extends UnpackedObject implements DbValue {

  private final IntegerProperty assigningTaskListenerIndexProp =
      new IntegerProperty("assigningTaskListenerIndex", 0);
  private final IntegerProperty updatingTaskListenerIndexProp =
      new IntegerProperty("updatingTaskListenerIndex", 0);
  private final IntegerProperty completingTaskListenerIndexProp =
      new IntegerProperty("completingTaskListenerIndex", 0);

  TaskListenerIndicesRecord() {
    super(3);
    declareProperty(assigningTaskListenerIndexProp)
        .declareProperty(updatingTaskListenerIndexProp)
        .declareProperty(completingTaskListenerIndexProp);
  }

  public int getTaskListenerIndex(final ZeebeTaskListenerEventType eventType) {
    return getTaskListenerIndexProp(eventType).getValue();
  }

  public void incrementTaskListenerIndex(final ZeebeTaskListenerEventType eventType) {
    getTaskListenerIndexProp(eventType).increment();
  }

  public void resetTaskListenerIndex(final ZeebeTaskListenerEventType eventType) {
    getTaskListenerIndexProp(eventType).reset();
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    final byte[] bytes = new byte[length];
    final UnsafeBuffer mutableBuffer = new UnsafeBuffer(bytes);
    buffer.getBytes(offset, bytes, 0, length);
    super.wrap(mutableBuffer, 0, length);
  }

  private IntegerProperty getTaskListenerIndexProp(final ZeebeTaskListenerEventType eventType) {
    return switch (eventType) {
      case assigning -> assigningTaskListenerIndexProp;
      case updating -> updatingTaskListenerIndexProp;
      case completing -> completingTaskListenerIndexProp;
      default ->
          throw new IllegalArgumentException("Unsupported ZeebeTaskListenerEventType " + eventType);
    };
  }
}
