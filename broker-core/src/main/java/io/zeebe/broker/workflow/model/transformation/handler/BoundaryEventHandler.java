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
import io.zeebe.broker.workflow.model.element.ExecutableActivity;
import io.zeebe.broker.workflow.model.element.ExecutableBoundaryEvent;
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.ModelElementTransformer;
import io.zeebe.broker.workflow.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.BoundaryEvent;
import io.zeebe.model.bpmn.instance.EventDefinition;
import io.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.time.Duration;

public class BoundaryEventHandler implements ModelElementTransformer<BoundaryEvent> {
  @Override
  public Class<BoundaryEvent> getType() {
    return BoundaryEvent.class;
  }

  @Override
  public void transform(BoundaryEvent event, TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableBoundaryEvent element =
        workflow.getElementById(event.getId(), ExecutableBoundaryEvent.class);

    element.setCancelActivity(event.cancelActivity());
    transformEventDefinition(event, element);
    attachToActivity(event, workflow, element);

    element.bindLifecycleState(
        WorkflowInstanceIntent.BOUNDARY_EVENT_TRIGGERED, BpmnStep.BOUNDARY_EVENT_TRIGGERED);
  }

  private void transformEventDefinition(BoundaryEvent event, ExecutableBoundaryEvent element) {
    final EventDefinition eventDefinition = event.getEventDefinitions().iterator().next();
    if (eventDefinition instanceof TimerEventDefinition) {
      transformTimerEventDefinition(element, (TimerEventDefinition) eventDefinition);
    }
  }

  private void transformTimerEventDefinition(
      ExecutableBoundaryEvent element, TimerEventDefinition eventDefinition) {
    final String timeDuration = eventDefinition.getTimeDuration().getTextContent();
    final Duration duration = Duration.parse(timeDuration);
    element.setDuration(duration);
  }

  private void attachToActivity(
      BoundaryEvent event, ExecutableWorkflow workflow, ExecutableBoundaryEvent element) {
    final Activity attachedToActivity = event.getAttachedTo();
    final ExecutableActivity attachedToElement =
        workflow.getElementById(attachedToActivity.getId(), ExecutableActivity.class);

    attachedToElement.attach(element);
    element.setAttachedTo(attachedToElement);
  }
}
