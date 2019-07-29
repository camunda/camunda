/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.transformer;

import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableActivity;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableLoopCharacteristics;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMultiInstanceBody;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.LoopCharacteristics;
import io.zeebe.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeLoopCharacteristics;
import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Optional;
import org.agrona.DirectBuffer;

public class MultiInstanceActivityTransformer implements ModelElementTransformer<Activity> {
  @Override
  public Class<Activity> getType() {
    return Activity.class;
  }

  @Override
  public void transform(final Activity element, final TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableActivity innerActivity =
        workflow.getElementById(element.getId(), ExecutableActivity.class);

    final LoopCharacteristics loopCharacteristics = element.getLoopCharacteristics();
    if (loopCharacteristics != null
        && loopCharacteristics instanceof MultiInstanceLoopCharacteristics) {

      final ExecutableLoopCharacteristics miLoopCharacteristics =
          transformLoopCharacteristics(
              context, innerActivity, (MultiInstanceLoopCharacteristics) loopCharacteristics);

      final ExecutableMultiInstanceBody multiInstanceBody =
          new ExecutableMultiInstanceBody(element.getId(), miLoopCharacteristics, innerActivity);

      // configure lifecycle of the body
      multiInstanceBody.bindLifecycleState(
          WorkflowInstanceIntent.ELEMENT_ACTIVATING, BpmnStep.MULTI_INSTANCE_ACTIVATING);
      multiInstanceBody.bindLifecycleState(
          WorkflowInstanceIntent.ELEMENT_ACTIVATED, BpmnStep.MULTI_INSTANCE_ACTIVATED);

      multiInstanceBody.bindLifecycleState(
          WorkflowInstanceIntent.ELEMENT_COMPLETING, BpmnStep.MULTI_INSTANCE_COMPLETING);
      multiInstanceBody.bindLifecycleState(
          WorkflowInstanceIntent.ELEMENT_COMPLETED, BpmnStep.FLOWOUT_ELEMENT_COMPLETED);

      multiInstanceBody.bindLifecycleState(
          WorkflowInstanceIntent.ELEMENT_TERMINATING, BpmnStep.MULTI_INSTANCE_TERMINATING);
      multiInstanceBody.bindLifecycleState(
          WorkflowInstanceIntent.ELEMENT_TERMINATED, BpmnStep.ACTIVITY_ELEMENT_TERMINATED);

      multiInstanceBody.bindLifecycleState(
          WorkflowInstanceIntent.EVENT_OCCURRED, BpmnStep.MULTI_INSTANCE_EVENT_OCCURRED);

      // avoid taking the outgoing sequence flow when inner activity is completed
      innerActivity.bindLifecycleState(
          WorkflowInstanceIntent.ELEMENT_COMPLETED, BpmnStep.ELEMENT_COMPLETED);

      // TODO (saig0) - #2855: attach boundary events to the multi-instance body
      innerActivity.getEvents().removeAll(innerActivity.getBoundaryEvents());
      innerActivity.getInterruptingElementIds().clear();

      // replace the inner element with the body
      workflow.addFlowElement(multiInstanceBody);
    }
  }

  private ExecutableLoopCharacteristics transformLoopCharacteristics(
      final TransformContext context,
      final ExecutableActivity activity,
      final MultiInstanceLoopCharacteristics elementLoopCharacteristics) {

    final boolean isSequential = elementLoopCharacteristics.isSequential();

    final ZeebeLoopCharacteristics zeebeLoopCharacteristics =
        elementLoopCharacteristics.getSingleExtensionElement(ZeebeLoopCharacteristics.class);

    final JsonPathQuery inputCollection =
        context.getJsonPathQueryCompiler().compile(zeebeLoopCharacteristics.getInputCollection());

    final Optional<DirectBuffer> inputElement =
        Optional.ofNullable(zeebeLoopCharacteristics.getInputElement())
            .filter(e -> !e.isEmpty())
            .map(BufferUtil::wrapString);

    return new ExecutableLoopCharacteristics(isSequential, inputCollection, inputElement);
  }
}
