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

  private final IntegerProperty createTaskListenerIndexProp =
      new IntegerProperty("createTaskListenerIndex", 0);
  private final IntegerProperty assignmentTaskListenerIndexProp =
      new IntegerProperty("assignmentTaskListenerIndex", 0);
  private final IntegerProperty updateTaskListenerIndexProp =
      new IntegerProperty("updateTaskListenerIndex", 0);
  private final IntegerProperty completeTaskListenerIndexProp =
      new IntegerProperty("completeTaskListenerIndex", 0);
  private final IntegerProperty cancelTaskListenerIndexProp =
      new IntegerProperty("cancelTaskListenerIndex", 0);

  TaskListenerIndicesRecord() {
    super(5);
    declareProperty(createTaskListenerIndexProp)
        .declareProperty(assignmentTaskListenerIndexProp)
        .declareProperty(updateTaskListenerIndexProp)
        .declareProperty(completeTaskListenerIndexProp)
        .declareProperty(cancelTaskListenerIndexProp);
  }

  public Integer getTaskListenerIndex(ZeebeTaskListenerEventType eventType) {
    return switch (eventType) {
      case create -> createTaskListenerIndexProp.getValue();
      case assignment -> assignmentTaskListenerIndexProp.getValue();
      case update -> updateTaskListenerIndexProp.getValue();
      case complete -> completeTaskListenerIndexProp.getValue();
      case cancel -> cancelTaskListenerIndexProp.getValue();
      default ->
          throw new IllegalArgumentException("Unexpected ZeebeTaskListenerEventType " + eventType);
    };
  }

  public void incrementTaskListenerIndex(ZeebeTaskListenerEventType eventType) {
    switch (eventType) {
      case create -> createTaskListenerIndexProp.increment();
      case assignment -> assignmentTaskListenerIndexProp.increment();
      case update -> updateTaskListenerIndexProp.increment();
      case complete -> completeTaskListenerIndexProp.increment();
      case cancel -> cancelTaskListenerIndexProp.increment();
      default ->
          throw new IllegalArgumentException("Unexpected ZeebeTaskListenerEventType " + eventType);
    }
  }

  public void resetTaskListenerIndex(ZeebeTaskListenerEventType eventType) {
    switch (eventType) {
      case create -> createTaskListenerIndexProp.reset();
      case assignment -> assignmentTaskListenerIndexProp.reset();
      case update -> updateTaskListenerIndexProp.reset();
      case complete -> completeTaskListenerIndexProp.reset();
      case cancel -> cancelTaskListenerIndexProp.reset();
      default ->
          throw new IllegalArgumentException("Unexpected ZeebeTaskListenerEventType " + eventType);
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
