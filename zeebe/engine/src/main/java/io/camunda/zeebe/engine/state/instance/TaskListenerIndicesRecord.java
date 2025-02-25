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

  private final IntegerProperty creatingTaskListenerIndexProp =
      new IntegerProperty("creatingTaskListenerIndex", 0);
  private final IntegerProperty assigningTaskListenerIndexProp =
      new IntegerProperty("assigningTaskListenerIndex", 0);
  private final IntegerProperty updatingTaskListenerIndexProp =
      new IntegerProperty("updatingTaskListenerIndex", 0);
  private final IntegerProperty completingTaskListenerIndexProp =
      new IntegerProperty("completingTaskListenerIndex", 0);

  TaskListenerIndicesRecord() {
    super(4);
    declareProperty(creatingTaskListenerIndexProp)
        .declareProperty(assigningTaskListenerIndexProp)
        .declareProperty(updatingTaskListenerIndexProp)
        .declareProperty(completingTaskListenerIndexProp);
  }

  public Integer getTaskListenerIndex(final ZeebeTaskListenerEventType eventType) {
    return switch (eventType) {
      case creating -> creatingTaskListenerIndexProp.getValue();
      case assigning -> assigningTaskListenerIndexProp.getValue();
      case updating -> updatingTaskListenerIndexProp.getValue();
      case completing -> completingTaskListenerIndexProp.getValue();
      default ->
          throw new IllegalArgumentException("Unsupported ZeebeTaskListenerEventType " + eventType);
    };
  }

  public void incrementTaskListenerIndex(final ZeebeTaskListenerEventType eventType) {
    switch (eventType) {
      case creating -> creatingTaskListenerIndexProp.increment();
      case assigning -> assigningTaskListenerIndexProp.increment();
      case updating -> updatingTaskListenerIndexProp.increment();
      case completing -> completingTaskListenerIndexProp.increment();
      default ->
          throw new IllegalArgumentException("Unsupported ZeebeTaskListenerEventType " + eventType);
    }
  }

  public void resetTaskListenerIndex(final ZeebeTaskListenerEventType eventType) {
    switch (eventType) {
      case creating -> creatingTaskListenerIndexProp.reset();
      case assigning -> assigningTaskListenerIndexProp.reset();
      case updating -> updatingTaskListenerIndexProp.reset();
      case completing -> completingTaskListenerIndexProp.reset();
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
}
