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
package io.zeebe.broker.workflow.processor.servicetask;

import io.zeebe.broker.logstreams.processor.TypedBatchWriter;
import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.flownode.TerminateElementHandler;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.JobIntent;
import org.agrona.concurrent.UnsafeBuffer;

public class TerminateServiceTaskHandler extends TerminateElementHandler {

  private static final UnsafeBuffer EMPTY_JOB_TYPE = new UnsafeBuffer("".getBytes());

  private final JobRecord jobRecord = new JobRecord();

  @Override
  protected void addTerminatingRecords(
      BpmnStepContext<ExecutableFlowNode> context, TypedBatchWriter batch) {

    final ElementInstance activityInstance = context.getElementInstance();

    final long jobKey = activityInstance.getJobKey();
    if (jobKey > 0) {
      final WorkflowInstanceRecord activityInstanceEvent = context.getValue();

      jobRecord.reset();
      jobRecord
          .setType(EMPTY_JOB_TYPE)
          .getHeaders()
          .setBpmnProcessId(activityInstanceEvent.getBpmnProcessId())
          .setWorkflowDefinitionVersion(activityInstanceEvent.getVersion())
          .setWorkflowInstanceKey(activityInstanceEvent.getWorkflowInstanceKey())
          .setActivityId(activityInstanceEvent.getActivityId())
          .setActivityInstanceKey(activityInstance.getKey());

      batch.addFollowUpCommand(jobKey, JobIntent.CANCEL, jobRecord);
    }
  }
}
