/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableAdHocSubProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableBoundaryEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableLoopCharacteristics;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.Activity;
import io.camunda.zeebe.model.bpmn.instance.CompletionCondition;
import io.camunda.zeebe.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeLoopCharacteristics;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collections;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class MultiInstanceActivityTransformer implements ModelElementTransformer<Activity> {
  @Override
  public Class<Activity> getType() {
    return Activity.class;
  }

  @Override
  public void transform(final Activity element, final TransformContext context) {
    final ExecutableProcess process = context.getCurrentProcess();
    final ExecutableActivity innerActivity =
        process.getElementById(element.getId(), ExecutableActivity.class);

    if (element.getLoopCharacteristics()
        instanceof final MultiInstanceLoopCharacteristics loopCharacteristics) {

      final ExecutableLoopCharacteristics miLoopCharacteristics =
          transformLoopCharacteristics(context, loopCharacteristics);

      final ExecutableMultiInstanceBody multiInstanceBody =
          new ExecutableMultiInstanceBody(element.getId(), miLoopCharacteristics, innerActivity);

      transformMultiInstanceBody(process, innerActivity, multiInstanceBody);
    }
  }

  private ExecutableLoopCharacteristics transformLoopCharacteristics(
      final TransformContext context,
      final MultiInstanceLoopCharacteristics elementLoopCharacteristics) {

    final boolean isSequential = elementLoopCharacteristics.isSequential();

    final Optional<Expression> completionCondition =
        Optional.ofNullable(elementLoopCharacteristics.getCompletionCondition())
            .map(CompletionCondition::getTextContent)
            .filter(e -> !e.isEmpty())
            .map(context.getExpressionLanguage()::parseExpression);

    final ZeebeLoopCharacteristics zeebeLoopCharacteristics =
        elementLoopCharacteristics.getSingleExtensionElement(ZeebeLoopCharacteristics.class);

    final Expression inputCollection =
        context
            .getExpressionLanguage()
            .parseExpression(zeebeLoopCharacteristics.getInputCollection());

    final Optional<DirectBuffer> inputElement =
        Optional.ofNullable(zeebeLoopCharacteristics.getInputElement())
            .filter(e -> !e.isEmpty())
            .map(BufferUtil::wrapString);

    final Optional<DirectBuffer> outputCollection =
        Optional.ofNullable(zeebeLoopCharacteristics.getOutputCollection())
            .filter(e -> !e.isEmpty())
            .map(BufferUtil::wrapString);

    final Optional<Expression> outputElement =
        Optional.ofNullable(zeebeLoopCharacteristics.getOutputElement())
            .filter(e -> !e.isEmpty())
            .map(e -> context.getExpressionLanguage().parseExpression(e));

    return new ExecutableLoopCharacteristics(
        isSequential,
        completionCondition,
        inputCollection,
        inputElement,
        outputCollection,
        outputElement);
  }

  private static void transformMultiInstanceBody(
      final ExecutableProcess process,
      final ExecutableActivity innerActivity,
      final ExecutableMultiInstanceBody multiInstanceBody) {

    multiInstanceBody.setElementType(BpmnElementType.MULTI_INSTANCE_BODY);

    multiInstanceBody.setFlowScope(innerActivity.getFlowScope());
    innerActivity.setFlowScope(multiInstanceBody);

    attachEventsToMultiInstanceBody(innerActivity, multiInstanceBody);
    connectSequenceFlowsToMultiInstanceBody(innerActivity, multiInstanceBody);
    replaceCompensationHandlerWithMultiInstanceBody(process, innerActivity, multiInstanceBody);
    replaceAdHocActivityWithMultiInstanceBody(process, innerActivity, multiInstanceBody);

    // replace the inner element with the body
    process.addFlowElement(multiInstanceBody);
  }

  private static void attachEventsToMultiInstanceBody(
      final ExecutableActivity innerActivity, final ExecutableMultiInstanceBody multiInstanceBody) {
    // attach boundary events to the multi-instance body
    innerActivity.getBoundaryEvents().forEach(multiInstanceBody::attach);

    innerActivity.getEvents().removeAll(innerActivity.getBoundaryEvents());
    innerActivity.getEventSubprocesses().stream()
        .map(ExecutableFlowElementContainer::getStartEvents)
        .forEach(innerActivity.getEvents()::remove);

    innerActivity.getInterruptingElementIds().clear();
    innerActivity.getBoundaryEvents().clear();
  }

  private static void connectSequenceFlowsToMultiInstanceBody(
      final ExecutableActivity innerActivity, final ExecutableMultiInstanceBody multiInstanceBody) {
    // attach incoming and outgoing sequence flows to the multi-instance body
    innerActivity.getIncoming().forEach(flow -> flow.setTarget(multiInstanceBody));
    innerActivity.getOutgoing().forEach(flow -> flow.setSource(multiInstanceBody));

    multiInstanceBody
        .getOutgoing()
        .addAll(Collections.unmodifiableList(innerActivity.getOutgoing()));
    innerActivity.getOutgoing().clear();
  }

  private static void replaceCompensationHandlerWithMultiInstanceBody(
      final ExecutableProcess process,
      final ExecutableActivity innerActivity,
      final ExecutableMultiInstanceBody multiInstanceBody) {
    // The compensation handler is set by other transformer before. Replace the inner activity with
    // the multi-instance body to invoke the body when a compensation is triggered.
    process.getFlowElements().stream()
        .filter(ExecutableBoundaryEvent.class::isInstance)
        .map(ExecutableBoundaryEvent.class::cast)
        .filter(boundaryEvent -> boundaryEvent.getEventType() == BpmnEventType.COMPENSATION)
        .map(ExecutableBoundaryEvent::getCompensation)
        .filter(compensation -> compensation.getCompensationHandler() == innerActivity)
        .forEach(compensation -> compensation.setCompensationHandler(multiInstanceBody));
  }

  private static void replaceAdHocActivityWithMultiInstanceBody(
      final ExecutableProcess process,
      final ExecutableActivity innerActivity,
      final ExecutableMultiInstanceBody multiInstanceBody) {
    // The ad-hoc activities are set by another transformer before. Replace the inner activity with
    // the multi-instance body to activate the body instead.
    process.getFlowElements().stream()
        .filter(ExecutableAdHocSubProcess.class::isInstance)
        .map(ExecutableAdHocSubProcess.class::cast)
        .forEach(
            adHocSubProcess ->
                adHocSubProcess.getAdHocActivitiesById().values().stream()
                    .filter(innerActivity::equals)
                    .forEach(adHocActivity -> adHocSubProcess.addAdHocActivity(multiInstanceBody)));
  }
}
