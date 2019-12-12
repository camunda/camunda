/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.transformer;

import static java.util.function.Predicate.not;

import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCallActivity;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.CallActivity;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.Optional;

public class CallActivityTransformer implements ModelElementTransformer<CallActivity> {

  @Override
  public Class<CallActivity> getType() {
    return CallActivity.class;
  }

  @Override
  public void transform(final CallActivity element, final TransformContext context) {

    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableCallActivity callActivity =
        workflow.getElementById(element.getId(), ExecutableCallActivity.class);

    transformProcessId(element, callActivity, context.getJsonPathQueryCompiler());

    bindLifecycle(callActivity);
  }

  private void bindLifecycle(final ExecutableCallActivity callActivity) {
    callActivity.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATING, BpmnStep.CALL_ACTIVITY_ACTIVATING);
    callActivity.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATING, BpmnStep.CALL_ACTIVITY_TERMINATING);
  }

  private void transformProcessId(
      final CallActivity element,
      final ExecutableCallActivity callActivity,
      final JsonPathQueryCompiler jsonPathQueryCompiler) {

    final ZeebeCalledElement calledElement =
        element.getSingleExtensionElement(ZeebeCalledElement.class);

    Optional.ofNullable(calledElement.getProcessId())
        .filter(not(String::isEmpty))
        .ifPresent(callActivity::setCalledElementProcessId);

    Optional.ofNullable(calledElement.getProcessIdExpression())
        .filter(not(String::isEmpty))
        .map(jsonPathQueryCompiler::compile)
        .ifPresent(callActivity::setCalledElementProcessIdExpression);
  }
}
