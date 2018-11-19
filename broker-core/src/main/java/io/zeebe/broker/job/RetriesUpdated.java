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
package io.zeebe.broker.job;

import io.zeebe.broker.incident.data.IncidentRecord;
import io.zeebe.broker.incident.processor.IncidentState;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.logstreams.state.ZeebeState;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.IncidentIntent;

public class RetriesUpdated implements TypedRecordProcessor<JobRecord> {

  private final ZeebeState zeebeState;

  public RetriesUpdated(ZeebeState zeebeState) {
    this.zeebeState = zeebeState;
  }

  @Override
  public void processRecord(
      TypedRecord<JobRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter) {
    final long jobKey = record.getKey();

    final IncidentState incidentState = zeebeState.getIncidentState();
    final long incidentKey = incidentState.getJobIncidentKey(jobKey);
    final IncidentRecord jobIncident = incidentState.getIncidentRecord(incidentKey);
    if (jobIncident != null) {
      streamWriter.appendFollowUpCommand(incidentKey, IncidentIntent.RESOLVE, jobIncident);
    }
  }
}
