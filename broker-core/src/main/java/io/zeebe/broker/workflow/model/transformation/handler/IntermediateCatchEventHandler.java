/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow.model.transformation.handler;

import io.zeebe.broker.workflow.model.BpmnStep;
import io.zeebe.broker.workflow.model.element.ExecutableIntermediateCatchElement;
import io.zeebe.broker.workflow.model.element.ExecutableMessage;
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.ModelElementTransformer;
import io.zeebe.broker.workflow.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.EventDefinition;
import io.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.time.Duration;

public class IntermediateCatchEventHandler
    implements ModelElementTransformer<IntermediateCatchEvent> {

  @Override
  public Class<IntermediateCatchEvent> getType() {
    return IntermediateCatchEvent.class;
  }

  @Override
  public void transform(IntermediateCatchEvent element, TransformContext context) {

    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableIntermediateCatchElement executableElement =
        workflow.getElementById(element.getId(), ExecutableIntermediateCatchElement.class);

    final EventDefinition eventDefinition = element.getEventDefinitions().iterator().next();

    bindDefaultLifecycle(context, executableElement);

    if (eventDefinition instanceof MessageEventDefinition) {
      transformMessageEventDefinition(
          context, executableElement, (MessageEventDefinition) eventDefinition);

    } else if (eventDefinition instanceof TimerEventDefinition) {
      transformTimerEventDefinition(executableElement, (TimerEventDefinition) eventDefinition);
    }
  }

  private void transformMessageEventDefinition(
      TransformContext context,
      final ExecutableIntermediateCatchElement executableElement,
      final MessageEventDefinition messageEventDefinition) {

    final Message message = messageEventDefinition.getMessage();
    final ExecutableMessage executableMessage = context.getMessage(message.getId());
    executableElement.setMessage(executableMessage);

    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATED, BpmnStep.SUBSCRIBE_TO_INTERMEDIATE_MESSAGE);
  }

  private void transformTimerEventDefinition(
      final ExecutableIntermediateCatchElement executableElement,
      final TimerEventDefinition timerEventDefinition) {

    final String timeDuration = timerEventDefinition.getTimeDuration().getTextContent();
    final Duration duration = Duration.parse(timeDuration);
    executableElement.setDuration(duration);

    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATED, BpmnStep.CREATE_TIMER);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATING, BpmnStep.TERMINATE_TIMER);
  }

  private void bindDefaultLifecycle(
      TransformContext context, final ExecutableIntermediateCatchElement executableElement) {

    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_READY, BpmnStep.APPLY_INPUT_MAPPING);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETING, BpmnStep.APPLY_OUTPUT_MAPPING);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETED, context.getCurrentFlowNodeOutgoingStep());
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATING, BpmnStep.TERMINATE_ELEMENT);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATED, BpmnStep.PROPAGATE_TERMINATION);
  }
}
