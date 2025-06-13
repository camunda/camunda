/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableAdHocSubProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class BpmnAdHocSubProcessBehavior {

  private final KeyGenerator keyGenerator;
  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;

  public BpmnAdHocSubProcessBehavior(final KeyGenerator keyGenerator, final Writers writers) {
    this.keyGenerator = keyGenerator;
    commandWriter = writers.command();
    stateWriter = writers.state();
  }

  public void activateElement(
      final ExecutableAdHocSubProcess adHocSubProcess,
      final BpmnElementContext context,
      final String elementIdToActivate) {

    // create the inner instance
    final ProcessInstanceRecord adHocSubprocessInnerInstanceRecord = new ProcessInstanceRecord();
    adHocSubprocessInnerInstanceRecord.wrap(context.getRecordValue());
    adHocSubprocessInnerInstanceRecord
        .setFlowScopeKey(context.getElementInstanceKey())
        .setElementId(adHocSubProcess.getInnerInstanceId())
        .setBpmnElementType(BpmnElementType.AD_HOC_SUB_PROCESS_INNER_INSTANCE)
        .setBpmnEventType(BpmnEventType.UNSPECIFIED);

    final long adHocSubProcessInnerInstanceKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(
        adHocSubProcessInnerInstanceKey,
        ProcessInstanceIntent.ELEMENT_ACTIVATING,
        adHocSubprocessInnerInstanceRecord);
    stateWriter.appendFollowUpEvent(
        adHocSubProcessInnerInstanceKey,
        ProcessInstanceIntent.ELEMENT_ACTIVATED,
        adHocSubprocessInnerInstanceRecord);

    // activate the element inside the inner instance
    final ExecutableFlowNode elementToActivate =
        adHocSubProcess.getAdHocActivitiesById().get(elementIdToActivate);

    final var elementProcessInstanceRecord = new ProcessInstanceRecord();
    elementProcessInstanceRecord.wrap(adHocSubprocessInnerInstanceRecord);
    elementProcessInstanceRecord
        .setFlowScopeKey(adHocSubProcessInnerInstanceKey)
        .setElementId(elementToActivate.getId())
        .setBpmnElementType(elementToActivate.getElementType())
        .setBpmnEventType(elementToActivate.getEventType());

    final long elementToActivateInstanceKey = keyGenerator.nextKey();
    commandWriter.appendFollowUpCommand(
        elementToActivateInstanceKey,
        ProcessInstanceIntent.ACTIVATE_ELEMENT,
        elementProcessInstanceRecord);
  }
}
