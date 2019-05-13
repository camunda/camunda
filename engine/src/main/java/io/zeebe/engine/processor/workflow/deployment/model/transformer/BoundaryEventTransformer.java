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

import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableActivity;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableBoundaryEvent;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.BoundaryEvent;

public class BoundaryEventTransformer implements ModelElementTransformer<BoundaryEvent> {
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
    attachToActivity(event, workflow, element);
  }

  private void attachToActivity(
      BoundaryEvent event, ExecutableWorkflow workflow, ExecutableBoundaryEvent element) {
    final Activity attachedToActivity = event.getAttachedTo();
    final ExecutableActivity attachedToElement =
        workflow.getElementById(attachedToActivity.getId(), ExecutableActivity.class);

    attachedToElement.attach(element);
  }
}
