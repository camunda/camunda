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
import io.camunda.zeebe.msgpack.property.BooleanProperty;
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

  // Static StringValue keys to avoid memory waste
  private static final StringValue PARENT_KEY = new StringValue("parentKey");
  private static final StringValue CHILD_COUNT = new StringValue("childCount");
  private static final StringValue CHILD_ACTIVATED_COUNT = new StringValue("childActivatedCount");
  private static final StringValue CHILD_COMPLETED_COUNT = new StringValue("childCompletedCount");
  private static final StringValue CHILD_TERMINATED_COUNT = new StringValue("childTerminatedCount");
  private static final StringValue JOB_KEY = new StringValue("jobKey");
  private static final StringValue MULTI_INSTANCE_LOOP_COUNTER =
      new StringValue("multiInstanceLoopCounter");
  private static final StringValue INTERRUPTING_ELEMENT_ID =
      new StringValue("interruptingElementId");
  private static final StringValue INTERRUPTED_BY_RUNTIME_INSTRUCTION =
      new StringValue("interruptedByRuntimeInstruction");
  private static final StringValue CALLED_CHILD_INSTANCE_KEY =
      new StringValue("calledChildInstanceKey");
  private static final StringValue ELEMENT_RECORD = new StringValue("elementRecord");
  private static final StringValue ACTIVE_SEQUENCE_FLOWS = new StringValue("activeSequenceFlows");
  private static final StringValue ACTIVE_SEQUENCE_FLOW_IDS =
      new StringValue("activeSequenceFlowIds");
  private static final StringValue USER_TASK_KEY = new StringValue("userTaskKey");
  private static final StringValue EXECUTION_LISTENER_INDEX =
      new StringValue("executionListenerIndex");
  private static final StringValue TASK_LISTENER_INDICES_RECORD =
      new StringValue("taskListenerIndicesRecord");
  private static final StringValue COMPLETION_CONDITION_FULFILLED =
      new StringValue("completionConditionFulfilled");
  private static final StringValue PROCESS_DEPTH = new StringValue("processDepth");
  private final LongProperty parentKeyProp = new LongProperty(PARENT_KEY, -1L);
  private final IntegerProperty childCountProp = new IntegerProperty(CHILD_COUNT, 0);
  private final IntegerProperty childActivatedCountProp =
      new IntegerProperty(CHILD_ACTIVATED_COUNT, 0);
  private final IntegerProperty childCompletedCountProp =
      new IntegerProperty(CHILD_COMPLETED_COUNT, 0);
  private final IntegerProperty childTerminatedCountProp =
      new IntegerProperty(CHILD_TERMINATED_COUNT, 0);
  private final LongProperty jobKeyProp = new LongProperty(JOB_KEY, 0L);
  private final IntegerProperty multiInstanceLoopCounterProp =
      new IntegerProperty(MULTI_INSTANCE_LOOP_COUNTER, 0);
  private final StringProperty interruptingEventKeyProp =
      new StringProperty(INTERRUPTING_ELEMENT_ID, "");
  private final BooleanProperty interruptedByRuntimeInstructionProp =
      new BooleanProperty(INTERRUPTED_BY_RUNTIME_INSTRUCTION, false);
  private final LongProperty calledChildInstanceKeyProp =
      new LongProperty(CALLED_CHILD_INSTANCE_KEY, -1L);
  private final ObjectProperty<IndexedRecord> recordProp =
      new ObjectProperty<>(ELEMENT_RECORD, new IndexedRecord());
  private final IntegerProperty activeSequenceFlowsProp =
      new IntegerProperty(ACTIVE_SEQUENCE_FLOWS, 0);
  private final ArrayProperty<StringValue> activeSequenceFlowIdsProp =
      new ArrayProperty<>(ACTIVE_SEQUENCE_FLOW_IDS, StringValue::new);
  private final LongProperty userTaskKeyProp = new LongProperty(USER_TASK_KEY, -1L);
  private final IntegerProperty executionListenerIndexProp =
      new IntegerProperty(EXECUTION_LISTENER_INDEX, 0);
  private final ObjectProperty<TaskListenerIndicesRecord> taskListenerIndicesRecordProp =
      new ObjectProperty<>(TASK_LISTENER_INDICES_RECORD, new TaskListenerIndicesRecord());
  private final IntegerProperty processDepth = new IntegerProperty(PROCESS_DEPTH, 1);
  private final BooleanProperty completionConditionFulfilledProp =
      new BooleanProperty(COMPLETION_CONDITION_FULFILLED, false);

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
  public ElementInstance() {
    super(18);
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
        .declareProperty(processDepth)
        .declareProperty(interruptedByRuntimeInstructionProp)
        .declareProperty(completionConditionFulfilledProp);
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

  public boolean isInterruptedByRuntimeInstruction() {
    return interruptedByRuntimeInstructionProp.getValue();
  }

  public void setInterruptedByRuntimeInstruction() {
    interruptedByRuntimeInstructionProp.setValue(true);
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

  public int getTaskListenerIndex(final ZeebeTaskListenerEventType eventType) {
    return taskListenerIndicesRecordProp.getValue().getTaskListenerIndex(eventType);
  }

  public void incrementTaskListenerIndex(final ZeebeTaskListenerEventType eventType) {
    taskListenerIndicesRecordProp.getValue().incrementTaskListenerIndex(eventType);
  }

  public void resetTaskListenerIndex(final ZeebeTaskListenerEventType eventType) {
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

  public boolean isCompletionConditionFulfilled() {
    return completionConditionFulfilledProp.getValue();
  }

  public void setCompletionConditionFulfilled(final boolean fulfilled) {
    completionConditionFulfilledProp.setValue(fulfilled);
  }
}
