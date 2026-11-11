/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.pojo.instance;

import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;

/** Plain data: index of the currently executed task listener per event type. */
@MsgpackPojo
public final class TaskListenerIndicesData {
  @MsgpackProperty("creatingTaskListenerIndex")
  private int creatingTaskListenerIndex;

  @MsgpackProperty("assigningTaskListenerIndex")
  private int assigningTaskListenerIndex;

  @MsgpackProperty("updatingTaskListenerIndex")
  private int updatingTaskListenerIndex;

  @MsgpackProperty("completingTaskListenerIndex")
  private int completingTaskListenerIndex;

  @MsgpackProperty("cancelingTaskListenerIndex")
  private int cancelingTaskListenerIndex;

  public int getCreatingTaskListenerIndex() {
    return creatingTaskListenerIndex;
  }

  public void setCreatingTaskListenerIndex(final int value) {
    creatingTaskListenerIndex = value;
  }

  public int getAssigningTaskListenerIndex() {
    return assigningTaskListenerIndex;
  }

  public void setAssigningTaskListenerIndex(final int value) {
    assigningTaskListenerIndex = value;
  }

  public int getUpdatingTaskListenerIndex() {
    return updatingTaskListenerIndex;
  }

  public void setUpdatingTaskListenerIndex(final int value) {
    updatingTaskListenerIndex = value;
  }

  public int getCompletingTaskListenerIndex() {
    return completingTaskListenerIndex;
  }

  public void setCompletingTaskListenerIndex(final int value) {
    completingTaskListenerIndex = value;
  }

  public int getCancelingTaskListenerIndex() {
    return cancelingTaskListenerIndex;
  }

  public void setCancelingTaskListenerIndex(final int value) {
    cancelingTaskListenerIndex = value;
  }
}
