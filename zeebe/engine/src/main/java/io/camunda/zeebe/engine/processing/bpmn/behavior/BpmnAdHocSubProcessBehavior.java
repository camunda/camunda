/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableAdHocSubProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class BpmnAdHocSubProcessBehavior {

  private static final DirectBuffer NO_VARIABLES = new UnsafeBuffer();
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final BpmnStateBehavior stateBehavior;
  private final VariableBehavior variableBehavior;
  private final ElementInstanceState elementInstanceState;

  public BpmnAdHocSubProcessBehavior(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final BpmnStateBehavior stateBehavior,
      final VariableBehavior variableBehavior,
      final ProcessingState processingState) {
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    commandWriter = writers.command();
    this.stateBehavior = stateBehavior;
    this.variableBehavior = variableBehavior;
    elementInstanceState = processingState.getElementInstanceState();
  }

  public void activateElement(
      final BpmnElementContext adHocSubProcessContext,
      final ExecutableAdHocSubProcess adHocSubProcess,
      final String elementToActivateId) {
    activateElement(adHocSubProcess, adHocSubProcessContext, elementToActivateId, NO_VARIABLES);
  }

  public void activateElement(
      final ExecutableAdHocSubProcess adHocSubProcess,
      final BpmnElementContext context,
      final String elementIdToActivate,
      final DirectBuffer variablesBuffer) {

    final long adHocSubProcessInnerInstanceKey = createInnerInstance(context, adHocSubProcess);

    adHocSubProcess
        .getOutputElement()
        .flatMap(Expression::getVariableName)
        .map(BufferUtil::wrapString)
        .ifPresent(
            variableName ->
                stateBehavior.setLocalVariable(
                    context, variableName, new UnsafeBuffer(MsgPackHelper.NIL)));

    if (variablesBuffer != NO_VARIABLES && variablesBuffer.capacity() > 0) {
      // set local variable in the scope of the inner instance
      variableBehavior.mergeLocalDocument(
          adHocSubProcessInnerInstanceKey,
          context.getProcessDefinitionKey(),
          context.getProcessInstanceKey(),
          context.getBpmnProcessId(),
          context.getTenantId(),
          variablesBuffer);
    }

    final ExecutableFlowNode elementToActivate =
        adHocSubProcess.getAdHocActivitiesById().get(elementIdToActivate);

    activateElement(context, elementToActivate, adHocSubProcessInnerInstanceKey);
  }

  private long createInnerInstance(
      final BpmnElementContext adHocSubProcessContext,
      final ExecutableAdHocSubProcess adHocSubProcess) {
    final var adHocSubProcessElementInstanceKey = adHocSubProcessContext.getElementInstanceKey();

    final var innerInstanceKey = keyGenerator.nextKey();
    final var innerInstanceRecord = new ProcessInstanceRecord();
    innerInstanceRecord.wrap(adHocSubProcessContext.getRecordValue());
    innerInstanceRecord
        .setFlowScopeKey(adHocSubProcessElementInstanceKey)
        .setElementId(adHocSubProcess.getInnerInstanceId())
        .setBpmnElementType(BpmnElementType.AD_HOC_SUB_PROCESS_INNER_INSTANCE)
        .setBpmnEventType(BpmnEventType.UNSPECIFIED);

    final var elementTreePath =
        stateBehavior.getElementTreePath(
            innerInstanceKey, adHocSubProcessElementInstanceKey, innerInstanceRecord);
    innerInstanceRecord.setElementInstancePath(elementTreePath.elementInstancePath());
    innerInstanceRecord.setProcessDefinitionPath(elementTreePath.processDefinitionPath());
    innerInstanceRecord.setCallingElementPath(elementTreePath.callingElementPath());

    stateWriter.appendFollowUpEvent(
        innerInstanceKey, ProcessInstanceIntent.ELEMENT_ACTIVATING, innerInstanceRecord);
    stateWriter.appendFollowUpEvent(
        innerInstanceKey, ProcessInstanceIntent.ELEMENT_ACTIVATED, innerInstanceRecord);
    return innerInstanceKey;
  }

  private void activateElement(
      final BpmnElementContext adHocSubProcessContext,
      final ExecutableFlowNode elementToActivate,
      final long innerInstanceKey) {
    final var innerElementRecord = new ProcessInstanceRecord();
    innerElementRecord.wrap(adHocSubProcessContext.getRecordValue());
    innerElementRecord
        .setFlowScopeKey(innerInstanceKey)
        .setElementId(elementToActivate.getId())
        .setBpmnElementType(elementToActivate.getElementType())
        .setBpmnEventType(elementToActivate.getEventType());

    final var elementInstanceKey = keyGenerator.nextKey();
    commandWriter.appendFollowUpCommand(
        elementInstanceKey, ProcessInstanceIntent.ACTIVATE_ELEMENT, innerElementRecord);
  }

  public void completionConditionFulfilled(
      final BpmnElementContext adHocSubProcessContext, final boolean cancelRemainingInstances) {
    final var adHocSubProcessInstance = stateBehavior.getElementInstance(adHocSubProcessContext);
    completionConditionFulfilled(
        adHocSubProcessContext, cancelRemainingInstances, adHocSubProcessInstance);
  }

  public void completionConditionFulfilled(
      final BpmnElementContext adHocSubProcessContext,
      final boolean cancelRemainingInstances,
      final ElementInstance adHocSubProcessInstance) {
    final var hasActiveChildInstances =
        adHocSubProcessInstance.getNumberOfActiveElementInstances() > 0;
    final var hasActiveSequenceFlows = adHocSubProcessInstance.getActiveSequenceFlows() > 0;

    if (cancelRemainingInstances) {
      // terminate all remaining child instances or directly complete ad-hoc sub-process if there
      // is no child activity left
      if (hasActiveChildInstances) {
        terminateChildInstances(adHocSubProcessContext);
      } else {
        completeAdHocSubProcess(adHocSubProcessContext);
      }

    } else if (!hasActiveChildInstances && !hasActiveSequenceFlows) {
      // complete ad-hoc sub-process if possible, otherwise skip completion as the same block
      // will be evaluated when the next activity is completed
      completeAdHocSubProcess(adHocSubProcessContext);
    }
  }

  private void terminateChildInstances(final BpmnElementContext adHocSubProcessContext) {
    elementInstanceState
        .getChildren(adHocSubProcessContext.getElementInstanceKey())
        .forEach(
            childInstance ->
                commandWriter.appendFollowUpCommand(
                    childInstance.getKey(),
                    ProcessInstanceIntent.TERMINATE_ELEMENT,
                    childInstance.getValue()));
  }

  private void completeAdHocSubProcess(final BpmnElementContext adHocSubProcessContext) {
    commandWriter.appendFollowUpCommand(
        adHocSubProcessContext.getElementInstanceKey(),
        ProcessInstanceIntent.COMPLETE_ELEMENT,
        adHocSubProcessContext.getRecordValue());
  }
}
