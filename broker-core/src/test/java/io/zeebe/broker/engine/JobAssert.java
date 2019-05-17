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
package io.zeebe.broker.engine;

import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.exporter.api.record.value.job.Headers;

public class JobAssert {

  public static final String PROCESS_ID = "process";

  public static void assertJobHeaders(long workflowInstanceKey, Record<JobRecordValue> jobRecord) {
    final Headers headers = jobRecord.getValue().getHeaders();
    Assertions.assertThat(headers)
        .hasElementId("task")
        .hasWorkflowDefinitionVersion(1)
        .hasBpmnProcessId(PROCESS_ID)
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }

  public static void assertJobHeaders(
      long workflowInstanceKey, String activityId, Record<JobRecordValue> jobRecord) {
    final Headers headers = jobRecord.getValue().getHeaders();
    Assertions.assertThat(headers)
        .hasElementId(activityId)
        .hasWorkflowDefinitionVersion(1)
        .hasBpmnProcessId(PROCESS_ID)
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }
}
