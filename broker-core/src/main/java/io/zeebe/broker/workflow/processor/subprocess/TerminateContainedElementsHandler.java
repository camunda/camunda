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
package io.zeebe.broker.workflow.processor.subprocess;

import io.zeebe.broker.workflow.index.ElementInstance;
import io.zeebe.broker.workflow.model.ExecutableSubProcess;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.BpmnStepHandler;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.List;

public class TerminateContainedElementsHandler implements BpmnStepHandler<ExecutableSubProcess> {

  @Override
  public void handle(BpmnStepContext<ExecutableSubProcess> context) {
    final ElementInstance subProcessInstance = context.getElementInstance();

    final List<ElementInstance> children = subProcessInstance.getChildren();

    for (int i = 0; i < children.size(); i++) {
      final ElementInstance child = children.get(i);

      if (child.canTerminate()) {
        context
            .getStreamWriter()
            .writeFollowUpEvent(
                child.getKey(), WorkflowInstanceIntent.ACTIVITY_TERMINATING, child.getValue());
      }
    }
  }
}
