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
package io.zeebe.broker.workflow.processor.job;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.protocol.impl.record.value.job.JobHeaders;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public final class JobCompletedEventProcessor implements TypedRecordProcessor<JobRecord> {

  private final WorkflowState workflowState;

  public JobCompletedEventProcessor(final WorkflowState workflowState) {
    this.workflowState = workflowState;
  }

  @Override
  public void processRecord(
      final TypedRecord<JobRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    final JobRecord jobEvent = record.getValue();
    final JobHeaders jobHeaders = jobEvent.getHeaders();
    final long elementInstanceKey = jobHeaders.getElementInstanceKey();
    final ElementInstance elementInstance =
        workflowState.getElementInstanceState().getInstance(elementInstanceKey);

    if (elementInstance != null) {

      final WorkflowInstanceRecord value = elementInstance.getValue();
      value.setPayload(jobEvent.getPayload());

      streamWriter.appendFollowUpEvent(
          elementInstanceKey, WorkflowInstanceIntent.ELEMENT_COMPLETING, value);
      elementInstance.setState(WorkflowInstanceIntent.ELEMENT_COMPLETING);
      elementInstance.setJobKey(-1);
      elementInstance.setValue(value);

      // TODO (saig0) #1860: if the task has no output mappings then the job payload should be
      // propagated to the task scope (not as local variables)

      // TODO (saig0) #1613: if the task has output mappings then the job payload should be set as
      // local variables to the task scope (which can be used in the output mappings)

      // TODO (saig0) #1613: don't override the task scope here because otherwise the output
      // mappings can't access variables which are not part of the job payload

      // remove any input-mapped variables, so they don't leak out on output mapping
      workflowState
          .getElementInstanceState()
          .getVariablesState()
          .removeAllVariables(elementInstanceKey);

      workflowState
          .getElementInstanceState()
          .getVariablesState()
          .setVariablesLocalFromDocument(elementInstanceKey, jobEvent.getPayload());

      workflowState.getElementInstanceState().flushDirtyState();
    }
  }
}
