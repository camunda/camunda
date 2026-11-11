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
import io.camunda.zeebe.msgpack.pojo.MsgpackIgnore;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Plain data; the msgpack (de)serializers are generated, see {@link MsgpackPojo}. The field names
 * and their order are the stored format.
 */
@MsgpackPojo
public final class TaskListenerIndicesRecord implements DbValue {

  int creatingTaskListenerIndex;
  int assigningTaskListenerIndex;
  int updatingTaskListenerIndex;
  int completingTaskListenerIndex;
  int cancelingTaskListenerIndex;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @MsgpackIgnore private TaskListenerIndicesRecordSerializer serializer;
  @MsgpackIgnore private TaskListenerIndicesRecordDeserializer deserializer;

  TaskListenerIndicesRecord() {}

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

  public void reset() {
    creatingTaskListenerIndex = 0;
    assigningTaskListenerIndex = 0;
    updatingTaskListenerIndex = 0;
    completingTaskListenerIndex = 0;
    cancelingTaskListenerIndex = 0;
  }

  // TODO: the DbValue contract below is kept as is until the column families stop requiring it.
  @Override
  public int getLength() {
    return serializer().getLength(this);
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    return serializer().serialize(this, buffer, offset);
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    if (deserializer == null) {
      deserializer = new TaskListenerIndicesRecordDeserializer();
    }
    deserializer.deserialize(buffer, offset, length, this);
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

  private TaskListenerIndicesRecordSerializer serializer() {
    if (serializer == null) {
      serializer = new TaskListenerIndicesRecordSerializer();
    }
    return serializer;
  }

  private static IllegalArgumentException unsupported(final ZeebeTaskListenerEventType eventType) {
    return new IllegalArgumentException("Unsupported ZeebeTaskListenerEventType " + eventType);
  }
}
