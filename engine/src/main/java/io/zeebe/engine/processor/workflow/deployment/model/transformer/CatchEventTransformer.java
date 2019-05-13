/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow.deployment.model.transformer;

import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEventElement;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMessage;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.CatchEvent;
import io.zeebe.model.bpmn.instance.EventDefinition;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.zeebe.model.bpmn.util.time.Interval;
import io.zeebe.model.bpmn.util.time.RepeatingInterval;
import io.zeebe.model.bpmn.util.time.TimeDateTimer;
import io.zeebe.model.bpmn.util.time.Timer;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class CatchEventTransformer implements ModelElementTransformer<CatchEvent> {
  @Override
  public Class<CatchEvent> getType() {
    return CatchEvent.class;
  }

  @Override
  public void transform(CatchEvent element, TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableCatchEventElement executableElement =
        workflow.getElementById(element.getId(), ExecutableCatchEventElement.class);

    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETED, BpmnStep.FLOWOUT_ELEMENT_COMPLETED);

    if (!element.getEventDefinitions().isEmpty()) {
      transformEventDefinition(element, context, executableElement);
    }
  }

  private void transformEventDefinition(
      CatchEvent element, TransformContext context, ExecutableCatchEventElement executableElement) {
    final EventDefinition eventDefinition = element.getEventDefinitions().iterator().next();
    if (eventDefinition instanceof MessageEventDefinition) {
      transformMessageEventDefinition(
          context, executableElement, (MessageEventDefinition) eventDefinition);
    } else if (eventDefinition instanceof TimerEventDefinition) {
      transformTimerEventDefinition(executableElement, (TimerEventDefinition) eventDefinition);
    }
  }

  private void transformMessageEventDefinition(
      TransformContext context,
      final ExecutableCatchEventElement executableElement,
      final MessageEventDefinition messageEventDefinition) {

    final Message message = messageEventDefinition.getMessage();
    final ExecutableMessage executableMessage = context.getMessage(message.getId());
    executableElement.setMessage(executableMessage);
  }

  private void transformTimerEventDefinition(
      final ExecutableCatchEventElement executableElement,
      final TimerEventDefinition timerEventDefinition) {
    final Timer timer;

    if (timerEventDefinition.getTimeDuration() != null) {
      final String duration = timerEventDefinition.getTimeDuration().getTextContent();
      timer = new RepeatingInterval(1, Interval.parse(duration));
    } else if (timerEventDefinition.getTimeCycle() != null) {
      final String cycle = timerEventDefinition.getTimeCycle().getTextContent();
      timer = RepeatingInterval.parse(cycle);
    } else {
      final String timeDate = timerEventDefinition.getTimeDate().getTextContent();
      timer = TimeDateTimer.parse(timeDate);
    }

    executableElement.setTimer(timer);
  }
}
