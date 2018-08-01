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
package io.zeebe.broker.workflow.processor.flownode;

import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.broker.workflow.map.ActivityInstanceMap;
import io.zeebe.broker.workflow.map.PayloadCache;
import io.zeebe.broker.workflow.map.WorkflowInstanceIndex;
import io.zeebe.broker.workflow.model.ExecutableFlowNode;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.BpmnStepHandler;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class ConsumeTokenHandler implements BpmnStepHandler<ExecutableFlowNode> {

  private final PayloadCache payloadCache;
  private final WorkflowInstanceIndex workflowInstanceIndex;
  private final ActivityInstanceMap activityInstanceMap;

  public ConsumeTokenHandler(
      PayloadCache payloadCache,
      WorkflowInstanceIndex workflowInstanceIndex,
      ActivityInstanceMap activityInstanceMap) {
    this.payloadCache = payloadCache;
    this.workflowInstanceIndex = workflowInstanceIndex;
    this.activityInstanceMap = activityInstanceMap;
  }

  @Override
  public void handle(BpmnStepContext<ExecutableFlowNode> context) {
    final WorkflowInstanceRecord value = context.getValue();

    final long scopeInstanceKey = value.getScopeInstanceKey();
    final long workflowInstanceKey = value.getWorkflowInstanceKey();

    if (scopeInstanceKey == workflowInstanceKey) {
      value.setScopeInstanceKey(-1);

      value.setActivityId("");
      context
          .getStreamWriter()
          .writeFollowUpEvent(workflowInstanceKey, WorkflowInstanceIntent.COMPLETED, value);

      workflowInstanceIndex.remove(workflowInstanceKey);
      payloadCache.remove(workflowInstanceKey);
    } else {
      activityInstanceMap.wrapActivityInstanceKey(scopeInstanceKey);

      value.setScopeInstanceKey(activityInstanceMap.getScopeInstanceKey());
      value.setActivityId(activityInstanceMap.getActivityId());

      context
          .getStreamWriter()
          .writeFollowUpEvent(
              workflowInstanceKey, WorkflowInstanceIntent.ACTIVITY_COMPLETING, value);
    }
  }
}
