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
package io.zeebe.broker.workflow.processor;

import io.zeebe.broker.logstreams.processor.KeyGenerator;
import io.zeebe.broker.workflow.processor.instance.CancelWorkflowInstanceHandler;
import io.zeebe.broker.workflow.processor.instance.CreateWorkflowInstanceHandler;
import io.zeebe.broker.workflow.processor.instance.UpdatePayloadHandler;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.HashMap;
import java.util.Map;

public class WorkflowInstanceCommandHandlers {

  private Map<WorkflowInstanceIntent, WorkflowInstanceCommandHandler> handlers = new HashMap<>();

  public WorkflowInstanceCommandHandlers(KeyGenerator keyGenerator, WorkflowState workflowState) {
    handlers.put(WorkflowInstanceIntent.CANCEL, new CancelWorkflowInstanceHandler());
    handlers.put(WorkflowInstanceIntent.UPDATE_PAYLOAD, new UpdatePayloadHandler(workflowState));
    handlers.put(
        WorkflowInstanceIntent.CREATE,
        new CreateWorkflowInstanceHandler(keyGenerator, workflowState));
  }

  public void handle(WorkflowInstanceCommandContext context) {
    final WorkflowInstanceCommandHandler handler = handlers.get(context.getCommand());
    if (handler != null) {
      handler.handle(context);
    }
  }
}
