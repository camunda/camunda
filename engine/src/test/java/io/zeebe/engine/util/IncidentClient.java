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
package io.zeebe.engine.util;

import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.IncidentRecordValue;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.test.util.record.RecordingExporter;

public class IncidentClient {

  private final StreamProcessorRule environmentRule;

  public IncidentClient(StreamProcessorRule environmentRule) {
    this.environmentRule = environmentRule;
  }

  public ResolveIncidentClient ofInstance(long workflowInstanceKey) {
    return new ResolveIncidentClient(environmentRule, workflowInstanceKey);
  }

  public static class ResolveIncidentClient {
    private static final long DEFAULT_KEY = -1L;

    private final StreamProcessorRule environmentRule;
    private final long workflowInstanceKey;
    private final IncidentRecord incidentRecord;

    private long incidentKey = DEFAULT_KEY;

    public ResolveIncidentClient(StreamProcessorRule environmentRule, long workflowInstanceKey) {
      this.environmentRule = environmentRule;
      this.workflowInstanceKey = workflowInstanceKey;
      incidentRecord = new IncidentRecord();
    }

    public ResolveIncidentClient withKey(long incidentKey) {
      this.incidentKey = incidentKey;
      return this;
    }

    public Record<IncidentRecordValue> resolve() {
      if (incidentKey == DEFAULT_KEY) {
        incidentKey =
            RecordingExporter.incidentRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .getFirst()
                .getKey();
      }

      final long position =
          environmentRule.writeCommandOnPartition(
              Protocol.decodePartitionId(incidentKey),
              incidentKey,
              IncidentIntent.RESOLVE,
              incidentRecord);

      return RecordingExporter.incidentRecords()
          .withWorkflowInstanceKey(workflowInstanceKey)
          .withRecordKey(incidentKey)
          .withSourceRecordPosition(position)
          .withIntent(IncidentIntent.RESOLVED)
          .getFirst();
    }
  }
}
