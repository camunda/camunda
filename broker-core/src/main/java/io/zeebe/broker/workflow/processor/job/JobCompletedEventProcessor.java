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

import io.zeebe.broker.job.data.JobHeaders;
import io.zeebe.broker.job.data.JobRecord;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.broker.workflow.index.ElementInstance;
import io.zeebe.broker.workflow.index.ElementInstanceIndex;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public final class JobCompletedEventProcessor implements TypedRecordProcessor<JobRecord> {

  private final ElementInstanceIndex scopeInstances;

  public JobCompletedEventProcessor(ElementInstanceIndex scopeInstances) {
    this.scopeInstances = scopeInstances;
  }

  @Override
  public void processRecord(
      TypedRecord<JobRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter) {

    final JobRecord jobEvent = record.getValue();
    final JobHeaders jobHeaders = jobEvent.headers();
    final long activityInstanceKey = jobHeaders.getActivityInstanceKey();
    final ElementInstance activityInstance = scopeInstances.getInstance(activityInstanceKey);

    if (activityInstance != null) {

      final WorkflowInstanceRecord value = activityInstance.getValue();
      value.setPayload(jobEvent.getPayload());

      streamWriter.writeFollowUpEvent(
          activityInstanceKey, WorkflowInstanceIntent.ELEMENT_COMPLETING, value);
      activityInstance.setState(WorkflowInstanceIntent.ELEMENT_COMPLETING);
      activityInstance.setJobKey(-1);
      activityInstance.setValue(value);
    }
  }
}
