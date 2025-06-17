/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.ElementTreePathBuilder.ElementTreePathProperties;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableAdHocSubProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class BpmnAdHocSubProcessBehavior {

  private static final UnsafeBuffer NO_VARIABLES = new UnsafeBuffer();

  private final KeyGenerator keyGenerator;
  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;
  private final BpmnStateBehavior stateBehavior;
  private final VariableBehavior variableBehavior;

  public BpmnAdHocSubProcessBehavior(
      final KeyGenerator keyGenerator,
      final Writers writers,
      final BpmnStateBehavior stateBehavior,
      final VariableBehavior variableBehavior) {
    this.keyGenerator = keyGenerator;
    commandWriter = writers.command();
    stateWriter = writers.state();
    this.stateBehavior = stateBehavior;
    this.variableBehavior = variableBehavior;
  }

  public void activateElement(
      final ExecutableAdHocSubProcess adHocSubProcess,
      final BpmnElementContext context,
      final String elementIdToActivate) {
    activateElement(adHocSubProcess, context, elementIdToActivate, NO_VARIABLES);
  }

  public void activateElement(
      final ExecutableAdHocSubProcess adHocSubProcess,
      final BpmnElementContext context,
      final String elementIdToActivate,
      final DirectBuffer variablesBuffer) {

    final long adHocSubProcessInnerInstanceKey =
        createAdHocSubProcessInnerInstance(adHocSubProcess, context);

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

    activateInnerElement(context, adHocSubProcessInnerInstanceKey, elementToActivate);
  }

  private long createAdHocSubProcessInnerInstance(
      final ExecutableAdHocSubProcess adHocSubProcess, final BpmnElementContext context) {
    final long adHocSubProcessElementInstanceKey = context.getElementInstanceKey();

    final long innerInstanceKey = keyGenerator.nextKey();

    final ProcessInstanceRecord innerInstanceRecord = new ProcessInstanceRecord();
    innerInstanceRecord.wrap(context.getRecordValue());
    innerInstanceRecord
        .setFlowScopeKey(adHocSubProcessElementInstanceKey)
        .setElementId(adHocSubProcess.getInnerInstanceId())
        .setBpmnElementType(BpmnElementType.AD_HOC_SUB_PROCESS_INNER_INSTANCE)
        .setBpmnEventType(BpmnEventType.UNSPECIFIED);

    final ElementTreePathProperties elementTreePath =
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

  private void activateInnerElement(
      final BpmnElementContext context,
      final long adHocSubProcessInnerInstanceKey,
      final ExecutableFlowNode elementToActivate) {

    final var elementRecord = new ProcessInstanceRecord();
    elementRecord.wrap(context.getRecordValue());
    elementRecord
        .setFlowScopeKey(adHocSubProcessInnerInstanceKey)
        .setElementId(elementToActivate.getId())
        .setBpmnElementType(elementToActivate.getElementType())
        .setBpmnEventType(elementToActivate.getEventType());

    final long elementInstanceKey = keyGenerator.nextKey();
    commandWriter.appendFollowUpCommand(
        elementInstanceKey, ProcessInstanceIntent.ACTIVATE_ELEMENT, elementRecord);
  }
}
