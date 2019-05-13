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
package io.zeebe.engine.processor.workflow.job;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.ElementInstance;
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

      streamWriter.appendFollowUpEvent(
          elementInstanceKey, WorkflowInstanceIntent.ELEMENT_COMPLETING, value);
      elementInstance.setState(WorkflowInstanceIntent.ELEMENT_COMPLETING);
      elementInstance.setJobKey(-1);
      elementInstance.setValue(value);
      workflowState.getElementInstanceState().updateInstance(elementInstance);

      workflowState.getEventScopeInstanceState().shutdownInstance(elementInstanceKey);
      workflowState
          .getElementInstanceState()
          .getVariablesState()
          .setTemporaryVariables(elementInstanceKey, jobEvent.getVariables());
    }
  }
}
