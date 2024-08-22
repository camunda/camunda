/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask.TaskListenerEventType;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class TaskListenerIndicesRecord extends UnpackedObject implements DbValue {

  private final IntegerProperty createTaskListenerIndexProp =
      new IntegerProperty("createTaskListenerIndex", 0);
  private final IntegerProperty assignTaskListenerIndexProp =
      new IntegerProperty("assignTaskListenerIndex", 0);
  private final IntegerProperty updateTaskListenerIndexProp =
      new IntegerProperty("updateTaskListenerIndex", 0);
  private final IntegerProperty completeTaskListenerIndexProp =
      new IntegerProperty("completeTaskListenerIndex", 0);

  TaskListenerIndicesRecord() {
    super(4);
    declareProperty(createTaskListenerIndexProp)
        .declareProperty(assignTaskListenerIndexProp)
        .declareProperty(updateTaskListenerIndexProp)
        .declareProperty(completeTaskListenerIndexProp);
  }

  public Integer getTaskListenerIndex(TaskListenerEventType event) {
    return switch (event) {
      case CREATE -> createTaskListenerIndexProp.getValue();
      case ASSIGN -> assignTaskListenerIndexProp.getValue();
      case UPDATE -> updateTaskListenerIndexProp.getValue();
      case COMPLETE -> completeTaskListenerIndexProp.getValue();
      default -> throw new IllegalArgumentException("Unexpected taskListenerEventType " + event);
    };
  }

  public void incrementTaskListenerIndex(TaskListenerEventType event) {
    switch (event) {
      case CREATE -> createTaskListenerIndexProp.increment();
      case ASSIGN -> assignTaskListenerIndexProp.increment();
      case UPDATE -> updateTaskListenerIndexProp.increment();
      case COMPLETE -> completeTaskListenerIndexProp.increment();
      default -> throw new IllegalArgumentException("Unexpected taskListenerEventType " + event);
    }
  }

  public void resetTaskListenerIndex(TaskListenerEventType event) {
    switch (event) {
      case CREATE -> createTaskListenerIndexProp.reset();
      case ASSIGN -> assignTaskListenerIndexProp.reset();
      case UPDATE -> updateTaskListenerIndexProp.reset();
      case COMPLETE -> completeTaskListenerIndexProp.reset();
      default -> throw new IllegalArgumentException("Unexpected taskListenerEventType " + event);
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
