/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.engine.processing.bpmn.ProcessInstanceLifecycle;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.agrona.DirectBuffer;

/**
 * Plain data; the msgpack codec is generated, see {@link MsgpackPojo}. The field names and their
 * order are the stored format.
 */
@MsgpackPojo
public final class ElementInstance extends MsgpackBufferValue<ElementInstance> implements DbValue {

  long parentKey;
  int childCount;
  int childActivatedCount;
  int childCompletedCount;
  int childTerminatedCount;
  long jobKey;
  int multiInstanceLoopCounter;
  DirectBuffer interruptingElementId;
  long calledChildInstanceKey;
  final IndexedRecord elementRecord = new IndexedRecord();
  int activeSequenceFlows;
  final List<DirectBuffer> activeSequenceFlowIds = new ArrayList<>();
  long userTaskKey;
  int executionListenerIndex;
  final TaskListenerIndicesRecord taskListenerIndicesRecord = new TaskListenerIndicesRecord();

  /**
   * Expresses the current depth of the process instance in the called process tree.
   *
   * <p>A root process instance has depth 1. Each child instance has the depth of its parent
   * incremented by 1.
   *
   * @since 8.3.22, 8.4.18, 8.5.17, 8.6.12, and 8.7
   * @apiNote This property is added in 8.7 and backported to 8.6.12, 8.5.17, 8.4.18, and 8.3.22.
   *     Any child process instances created before 8.7 (or any of these patches) will have a depth
   *     of 1 rather than a correct value. Child instances created before the property existed will
   *     have a depth of 1 + the depth of the parent instance. Therefore, child instances created on
   *     or after the property was added that are part of a root process instance created prior to
   *     the property existed, will not have a correct depth.
   */
  int processDepth;

  boolean interruptedByRuntimeInstruction;
  boolean completionConditionFulfilled;
  long agentInstanceKey;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public ElementInstance() {
    reset();
  }

  public ElementInstance(
      final long key,
      final ElementInstance parent,
      final ProcessInstanceIntent state,
      final ProcessInstanceRecord value) {
    this();

    elementRecord.setKey(key);
    elementRecord.setState(state);
    elementRecord.setValue(value);
    if (parent != null) {
      parentKey = parent.getKey();
      parent.childCount++;
    }
  }

  public ElementInstance(
      final long key, final ProcessInstanceIntent state, final ProcessInstanceRecord value) {
    this(key, null, state, value);
  }

  public long getKey() {
    return elementRecord.getKey();
  }

  public ProcessInstanceIntent getState() {
    return elementRecord.getState();
  }

  public void setState(final ProcessInstanceIntent state) {
    elementRecord.setState(state);
  }

  public ProcessInstanceRecord getValue() {
    return elementRecord.getValue();
  }

  public void setValue(final ProcessInstanceRecord value) {
    elementRecord.setValue(value);
  }

  public long getJobKey() {
    return jobKey;
  }

  public void setJobKey(final long jobKey) {
    this.jobKey = jobKey;
  }

  public void decrementChildCount() {
    final int newChildCount = --childCount;

    if (newChildCount < 0) {
      throw new IllegalStateException(
          String.format("Expected the child count to be positive but was %d", newChildCount));
    }
  }

  public boolean canTerminate() {
    return ProcessInstanceLifecycle.canTerminate(getState());
  }

  public boolean isActive() {
    return ProcessInstanceLifecycle.isActive(getState());
  }

  public boolean isTerminating() {
    return ProcessInstanceLifecycle.isTerminating(getState());
  }

  public boolean isInFinalState() {
    return ProcessInstanceLifecycle.isFinalState(getState());
  }

  public int getNumberOfActiveElementInstances() {
    return childCount;
  }

  public int getNumberOfCompletedElementInstances() {
    return childCompletedCount;
  }

  public int getNumberOfElementInstances() {
    return childActivatedCount;
  }

  public int getNumberOfTerminatedElementInstances() {
    return childTerminatedCount;
  }

  public void incrementNumberOfCompletedElementInstances() {
    childCompletedCount++;
  }

  public void incrementNumberOfElementInstances() {
    childActivatedCount++;
  }

  public void incrementNumberOfTerminatedElementInstances() {
    childTerminatedCount++;
  }

  public int getMultiInstanceLoopCounter() {
    return multiInstanceLoopCounter;
  }

  public void setMultiInstanceLoopCounter(final int loopCounter) {
    multiInstanceLoopCounter = loopCounter;
  }

  public void incrementMultiInstanceLoopCounter() {
    multiInstanceLoopCounter++;
  }

  public long getCalledChildInstanceKey() {
    return calledChildInstanceKey;
  }

  public void setCalledChildInstanceKey(final long calledChildInstanceKey) {
    this.calledChildInstanceKey = calledChildInstanceKey;
  }

  public DirectBuffer getInterruptingElementId() {
    return interruptingElementId;
  }

  public void setInterruptingElementId(final DirectBuffer elementId) {
    // the caller may reuse its buffer, this instance keeps its own copy
    interruptingElementId = BufferUtil.cloneBuffer(elementId);
  }

  public boolean isInterruptedByRuntimeInstruction() {
    return interruptedByRuntimeInstruction;
  }

  public void setInterruptedByRuntimeInstruction() {
    interruptedByRuntimeInstruction = true;
  }

  public boolean isInterrupted() {
    return getInterruptingElementId().capacity() > 0;
  }

  public void clearInterruptedState() {
    interruptingElementId = Decoding.EMPTY;
  }

  public long getParentKey() {
    return parentKey;
  }

  public int getActiveSequenceFlows() {
    return activeSequenceFlows;
  }

  public void decrementActiveSequenceFlows() {
    if (getActiveSequenceFlows() > 0) {
      activeSequenceFlows--;
      // This should never happen, but we should fix this in a better way
      // https://github.com/camunda/camunda/issues/9528
      //    if (decrement < 0) {
      //      throw new IllegalStateException(
      //          "Not expected to have an active sequence flow count lower then zero!");
      //    }
    }
  }

  public void decrementActiveSequenceFlows(final int amount) {
    final var current = getActiveSequenceFlows();
    if (current > 0) {
      activeSequenceFlows = Math.max(0, current - amount);
    }
  }

  public void incrementActiveSequenceFlows() {
    activeSequenceFlows++;
  }

  public void addActiveSequenceFlowId(final DirectBuffer sequenceFlowId) {
    activeSequenceFlowIds.add(BufferUtil.cloneBuffer(sequenceFlowId));
  }

  public void removeActiveSequenceFlowId(final DirectBuffer sequenceFlowId) {
    final Iterator<DirectBuffer> iterator = activeSequenceFlowIds.iterator();
    while (iterator.hasNext()) {
      if (iterator.next().equals(sequenceFlowId)) {
        iterator.remove();
        return;
      }
    }
  }

  public void resetActiveSequenceFlows() {
    activeSequenceFlows = 0;
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

  public void incrementExecutionListenerIndex() {
    executionListenerIndex++;
  }

  public void resetExecutionListenerIndex() {
    executionListenerIndex = 0;
  }

  public int getTaskListenerIndex(final ZeebeTaskListenerEventType eventType) {
    return taskListenerIndicesRecord.getTaskListenerIndex(eventType);
  }

  public void incrementTaskListenerIndex(final ZeebeTaskListenerEventType eventType) {
    taskListenerIndicesRecord.incrementTaskListenerIndex(eventType);
  }

  public void resetTaskListenerIndex(final ZeebeTaskListenerEventType eventType) {
    taskListenerIndicesRecord.resetTaskListenerIndex(eventType);
  }

  public void resetTaskListenerIndices() {
    taskListenerIndicesRecord.reset();
  }

  /**
   * Returns a list of currently active sequence flow ids. If the same sequence flow is active
   * multiple times, it will appear in the list multiple times. I.e. this can be used to track
   * virtual sequence flow instances. Virtual, because there are no sequence flow instances in
   * Zeebe.
   *
   * <p>Warning, this method should not be used for process instances created before 8.6. It may
   * provide incorrect information for such process instances.
   *
   * @since 8.6
   */
  public List<DirectBuffer> getActiveSequenceFlowIds() {
    return List.copyOf(activeSequenceFlowIds);
  }

  public int getProcessDepth() {
    return processDepth;
  }

  public void setProcessDepth(final int depth) {
    processDepth = depth;
  }

  public boolean isCompletionConditionFulfilled() {
    return completionConditionFulfilled;
  }

  public void setCompletionConditionFulfilled(final boolean fulfilled) {
    completionConditionFulfilled = fulfilled;
  }

  public long getAgentInstanceKey() {
    return agentInstanceKey;
  }

  public void setAgentInstanceKey(final long agentInstanceKey) {
    this.agentInstanceKey = agentInstanceKey;
  }

  /** The only place with the default of every property. */
  @Override
  public void reset() {
    parentKey = -1L;
    childCount = 0;
    childActivatedCount = 0;
    childCompletedCount = 0;
    childTerminatedCount = 0;
    jobKey = 0L;
    multiInstanceLoopCounter = 0;
    interruptingElementId = Decoding.EMPTY;
    calledChildInstanceKey = -1L;
    elementRecord.reset();
    activeSequenceFlows = 0;
    activeSequenceFlowIds.clear();
    userTaskKey = -1L;
    executionListenerIndex = 0;
    taskListenerIndicesRecord.reset();
    processDepth = 1;
    interruptedByRuntimeInstruction = false;
    completionConditionFulfilled = false;
    agentInstanceKey = -1L;
    unknownProperties.clear();
  }

  @Override
  protected MsgpackCodec<ElementInstance> codec() {
    return ElementInstanceCodec.INSTANCE;
  }
}
