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
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.Iterator;
import java.util.List;
import org.agrona.DirectBuffer;

public final class ElementInstance extends UnpackedObject implements DbValue {

  private final LongProperty parentKeyProp = new LongProperty("parentKey", -1L);
  private final IntegerProperty childCountProp = new IntegerProperty("childCount", 0);
  private final IntegerProperty childActivatedCountProp =
      new IntegerProperty("childActivatedCount", 0);
  private final IntegerProperty childCompletedCountProp =
      new IntegerProperty("childCompletedCount", 0);
  private final IntegerProperty childTerminatedCountProp =
      new IntegerProperty("childTerminatedCount", 0);
  private final LongProperty jobKeyProp = new LongProperty("jobKey", 0L);
  private final IntegerProperty multiInstanceLoopCounterProp =
      new IntegerProperty("multiInstanceLoopCounter", 0);
  private final StringProperty interruptingEventKeyProp =
      new StringProperty("interruptingElementId", "");
  private final LongProperty calledChildInstanceKeyProp =
      new LongProperty("calledChildInstanceKey", -1L);
  private final ObjectProperty<IndexedRecord> recordProp =
      new ObjectProperty<>("elementRecord", new IndexedRecord());
  private final IntegerProperty activeSequenceFlowsProp =
      new IntegerProperty("activeSequenceFlows", 0);
  private final ArrayProperty<StringValue> activeSequenceFlowIdsProp =
      new ArrayProperty<>("activeSequenceFlowIds", StringValue::new);
  private final LongProperty userTaskKeyProp = new LongProperty("userTaskKey", -1L);
  private final IntegerProperty executionListenerIndexProp =
      new IntegerProperty("executionListenerIndex", 0);
  private final ObjectProperty<TaskListenerIndicesRecord> taskListenerIndicesRecordProp =
      new ObjectProperty<>("taskListenerIndicesRecord", new TaskListenerIndicesRecord());

  /**
   * Expresses the current depth of the process instance in the called process tree.
   *
   * <p>A root process instance has depth 1. Each child instance has the depth of its parent
   * incremented by 1.
   *
   * @since 8.7
   * @apiNote This value is added in 8.7, any child process instances created before 8.7 will have a
   *     depth of 1 rather than a correct value. Child instances created on or after 8.7 will have a
   *     depth of 1 + the depth of the parent instance. Therefore, child instances created on or
   *     after 8.7 that are part of a root process instance created prior to 8.7, will not have a
   *     correct depth.
   */
  private final IntegerProperty processDepth = new IntegerProperty("processDepth", 1);

  public ElementInstance() {
    super(16);
    declareProperty(parentKeyProp)
        .declareProperty(childCountProp)
        .declareProperty(childActivatedCountProp)
        .declareProperty(childCompletedCountProp)
        .declareProperty(childTerminatedCountProp)
        .declareProperty(jobKeyProp)
        .declareProperty(multiInstanceLoopCounterProp)
        .declareProperty(interruptingEventKeyProp)
        .declareProperty(calledChildInstanceKeyProp)
        .declareProperty(recordProp)
        .declareProperty(activeSequenceFlowsProp)
        .declareProperty(activeSequenceFlowIdsProp)
        .declareProperty(userTaskKeyProp)
        .declareProperty(executionListenerIndexProp)
        .declareProperty(taskListenerIndicesRecordProp)
        .declareProperty(processDepth);
  }

  public ElementInstance(
      final long key,
      final ElementInstance parent,
      final ProcessInstanceIntent state,
      final ProcessInstanceRecord value) {
    this();

    recordProp.getValue().setKey(key);
    recordProp.getValue().setState(state);
    recordProp.getValue().setValue(value);
    if (parent != null) {
      parentKeyProp.setValue(parent.getKey());
      parent.childCountProp.increment();
    }
  }

  public ElementInstance(
      final long key, final ProcessInstanceIntent state, final ProcessInstanceRecord value) {
    this(key, null, state, value);
  }

  public long getKey() {
    return recordProp.getValue().getKey();
  }

  public ProcessInstanceIntent getState() {
    return recordProp.getValue().getState();
  }

  public void setState(final ProcessInstanceIntent state) {
    recordProp.getValue().setState(state);
  }

  public ProcessInstanceRecord getValue() {
    return recordProp.getValue().getValue();
  }

  public void setValue(final ProcessInstanceRecord value) {
    recordProp.getValue().setValue(value);
  }

  public long getJobKey() {
    return jobKeyProp.getValue();
  }

  public void setJobKey(final long jobKey) {
    jobKeyProp.setValue(jobKey);
  }

  public void decrementChildCount() {
    final int childCount = childCountProp.decrement();

    if (childCount < 0) {
      throw new IllegalStateException(
          String.format("Expected the child count to be positive but was %d", childCount));
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
    return childCountProp.getValue();
  }

  public int getNumberOfCompletedElementInstances() {
    return childCompletedCountProp.getValue();
  }

  public int getNumberOfElementInstances() {
    return childActivatedCountProp.getValue();
  }

  public int getNumberOfTerminatedElementInstances() {
    return childTerminatedCountProp.getValue();
  }

  public void incrementNumberOfCompletedElementInstances() {
    childCompletedCountProp.increment();
  }

  public void incrementNumberOfElementInstances() {
    childActivatedCountProp.increment();
  }

  public void incrementNumberOfTerminatedElementInstances() {
    childTerminatedCountProp.increment();
  }

  public int getMultiInstanceLoopCounter() {
    return multiInstanceLoopCounterProp.getValue();
  }

  public void setMultiInstanceLoopCounter(final int loopCounter) {
    multiInstanceLoopCounterProp.setValue(loopCounter);
  }

  public void incrementMultiInstanceLoopCounter() {
    multiInstanceLoopCounterProp.increment();
  }

  public long getCalledChildInstanceKey() {
    return calledChildInstanceKeyProp.getValue();
  }

  public void setCalledChildInstanceKey(final long calledChildInstanceKey) {
    calledChildInstanceKeyProp.setValue(calledChildInstanceKey);
  }

  public DirectBuffer getInterruptingElementId() {
    return interruptingEventKeyProp.getValue();
  }

  public void setInterruptingElementId(final DirectBuffer elementId) {
    interruptingEventKeyProp.setValue(elementId);
  }

  public boolean isInterrupted() {
    return getInterruptingElementId().capacity() > 0;
  }

  public void clearInterruptedState() {
    interruptingEventKeyProp.setValue("");
  }

  public long getParentKey() {
    return parentKeyProp.getValue();
  }

  public long getActiveSequenceFlows() {
    return activeSequenceFlowsProp.getValue();
  }

  public void decrementActiveSequenceFlows() {
    if (getActiveSequenceFlows() > 0) {
      activeSequenceFlowsProp.decrement();
      // This should never happen, but we should fix this in a better way
      // https://github.com/camunda/camunda/issues/9528
      //    if (decrement < 0) {
      //      throw new IllegalStateException(
      //          "Not expected to have an active sequence flow count lower then zero!");
      //    }
    }
  }

  public void incrementActiveSequenceFlows() {
    activeSequenceFlowsProp.increment();
  }

  public void addActiveSequenceFlowId(final DirectBuffer sequenceFlowId) {
    activeSequenceFlowIdsProp.add().wrap(sequenceFlowId);
  }

  public void removeActiveSequenceFlowId(final DirectBuffer sequenceFlowId) {
    final Iterator<StringValue> iterator = activeSequenceFlowIdsProp.iterator();
    while (iterator.hasNext()) {
      if (iterator.next().getValue().equals(sequenceFlowId)) {
        iterator.remove();
        return;
      }
    }
  }

  public void resetActiveSequenceFlows() {
    activeSequenceFlowsProp.setValue(0);
  }

  public long getUserTaskKey() {
    return userTaskKeyProp.getValue();
  }

  public void setUserTaskKey(final long userTaskKey) {
    userTaskKeyProp.setValue(userTaskKey);
  }

  public int getExecutionListenerIndex() {
    return executionListenerIndexProp.getValue();
  }

  public void incrementExecutionListenerIndex() {
    executionListenerIndexProp.increment();
  }

  public void resetExecutionListenerIndex() {
    executionListenerIndexProp.reset();
  }

  public Integer getTaskListenerIndex(ZeebeTaskListenerEventType eventType) {
    return taskListenerIndicesRecordProp.getValue().getTaskListenerIndex(eventType);
  }

  public void incrementTaskListenerIndex(ZeebeTaskListenerEventType eventType) {
    taskListenerIndicesRecordProp.getValue().incrementTaskListenerIndex(eventType);
  }

  public void resetTaskListenerIndex(ZeebeTaskListenerEventType eventType) {
    taskListenerIndicesRecordProp.getValue().resetTaskListenerIndex(eventType);
  }

  public void resetTaskListenerIndices() {
    taskListenerIndicesRecordProp.getValue().reset();
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
    return activeSequenceFlowIdsProp.stream().map(StringValue::getValue).toList();
  }

  public int getProcessDepth() {
    return processDepth.getValue();
  }

  public void setProcessDepth(final int depth) {
    processDepth.setValue(depth);
  }
}
