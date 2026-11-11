/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.pojo.instance;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;

/**
 * Plain data of an element instance as stored in the state. Property order is the stored order.
 * Buffers are stored by reference: whoever sets one keeps ownership and must not modify it while
 * this object is in use. Nested objects and the list are owned by this object.
 */
@MsgpackPojo
public final class ElementInstanceData {
  @MsgpackProperty("parentKey")
  private long parentKey = -1L;

  @MsgpackProperty("childCount")
  private int childCount = 0;

  @MsgpackProperty("childActivatedCount")
  private int childActivatedCount = 0;

  @MsgpackProperty("childCompletedCount")
  private int childCompletedCount = 0;

  @MsgpackProperty("childTerminatedCount")
  private int childTerminatedCount = 0;

  @MsgpackProperty("jobKey")
  private long jobKey = 0L;

  @MsgpackProperty("multiInstanceLoopCounter")
  private int multiInstanceLoopCounter = 0;

  @MsgpackProperty("interruptingElementId")
  private DirectBuffer interruptingElementId = Decoding.EMPTY;

  @MsgpackProperty("calledChildInstanceKey")
  private long calledChildInstanceKey = -1L;

  @MsgpackProperty("elementRecord")
  private IndexedRecordData elementRecord = new IndexedRecordData();

  @MsgpackProperty("activeSequenceFlows")
  private int activeSequenceFlows = 0;

  @MsgpackProperty("activeSequenceFlowIds")
  private List<DirectBuffer> activeSequenceFlowIds = new ArrayList<>();

  @MsgpackProperty("userTaskKey")
  private long userTaskKey = -1L;

  @MsgpackProperty("executionListenerIndex")
  private int executionListenerIndex = 0;

  @MsgpackProperty("taskListenerIndicesRecord")
  private TaskListenerIndicesData taskListenerIndicesRecord = new TaskListenerIndicesData();

  @MsgpackProperty("processDepth")
  private int processDepth = 1;

  @MsgpackProperty("interruptedByRuntimeInstruction")
  private boolean interruptedByRuntimeInstruction = false;

  @MsgpackProperty("completionConditionFulfilled")
  private boolean completionConditionFulfilled = false;

  @MsgpackProperty("agentInstanceKey")
  private long agentInstanceKey = -1L;

  @MsgpackUnknownProperties
  private final UnknownProperties unknownProperties = new UnknownProperties();

  public long getParentKey() {
    return parentKey;
  }

  public void setParentKey(final long parentKey) {
    this.parentKey = parentKey;
  }

  public int getChildCount() {
    return childCount;
  }

  public void setChildCount(final int childCount) {
    this.childCount = childCount;
  }

  public int getChildActivatedCount() {
    return childActivatedCount;
  }

  public void setChildActivatedCount(final int childActivatedCount) {
    this.childActivatedCount = childActivatedCount;
  }

  public int getChildCompletedCount() {
    return childCompletedCount;
  }

  public void setChildCompletedCount(final int childCompletedCount) {
    this.childCompletedCount = childCompletedCount;
  }

  public int getChildTerminatedCount() {
    return childTerminatedCount;
  }

  public void setChildTerminatedCount(final int childTerminatedCount) {
    this.childTerminatedCount = childTerminatedCount;
  }

  public long getJobKey() {
    return jobKey;
  }

  public void setJobKey(final long jobKey) {
    this.jobKey = jobKey;
  }

  public int getMultiInstanceLoopCounter() {
    return multiInstanceLoopCounter;
  }

  public void setMultiInstanceLoopCounter(final int multiInstanceLoopCounter) {
    this.multiInstanceLoopCounter = multiInstanceLoopCounter;
  }

  public DirectBuffer getInterruptingElementId() {
    return interruptingElementId;
  }

  public void setInterruptingElementId(final DirectBuffer interruptingElementId) {
    this.interruptingElementId = interruptingElementId;
  }

  public long getCalledChildInstanceKey() {
    return calledChildInstanceKey;
  }

  public void setCalledChildInstanceKey(final long calledChildInstanceKey) {
    this.calledChildInstanceKey = calledChildInstanceKey;
  }

  public IndexedRecordData getElementRecord() {
    return elementRecord;
  }

  public int getActiveSequenceFlows() {
    return activeSequenceFlows;
  }

  public void setActiveSequenceFlows(final int activeSequenceFlows) {
    this.activeSequenceFlows = activeSequenceFlows;
  }

  public List<DirectBuffer> getActiveSequenceFlowIds() {
    return activeSequenceFlowIds;
  }

  public long getUserTaskKey() {
    return userTaskKey;
  }

  public void setUserTaskKey(final long userTaskKey) {
    this.userTaskKey = userTaskKey;
  }

  public int getExecutionListenerIndex() {
    return executionListenerIndex;
  }

  public void setExecutionListenerIndex(final int executionListenerIndex) {
    this.executionListenerIndex = executionListenerIndex;
  }

  public TaskListenerIndicesData getTaskListenerIndicesRecord() {
    return taskListenerIndicesRecord;
  }

  public int getProcessDepth() {
    return processDepth;
  }

  public void setProcessDepth(final int processDepth) {
    this.processDepth = processDepth;
  }

  public boolean isInterruptedByRuntimeInstruction() {
    return interruptedByRuntimeInstruction;
  }

  public void setInterruptedByRuntimeInstruction(final boolean interruptedByRuntimeInstruction) {
    this.interruptedByRuntimeInstruction = interruptedByRuntimeInstruction;
  }

  public boolean isCompletionConditionFulfilled() {
    return completionConditionFulfilled;
  }

  public void setCompletionConditionFulfilled(final boolean completionConditionFulfilled) {
    this.completionConditionFulfilled = completionConditionFulfilled;
  }

  public long getAgentInstanceKey() {
    return agentInstanceKey;
  }

  public void setAgentInstanceKey(final long agentInstanceKey) {
    this.agentInstanceKey = agentInstanceKey;
  }

  public UnknownProperties getUnknownProperties() {
    return unknownProperties;
  }
}
