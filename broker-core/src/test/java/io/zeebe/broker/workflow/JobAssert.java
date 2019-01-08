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
package io.zeebe.broker.workflow;

import static io.zeebe.broker.workflow.gateway.ParallelGatewayStreamProcessorTest.PROCESS_ID;

import io.zeebe.exporter.record.Assertions;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.JobRecordValue;
import io.zeebe.exporter.record.value.job.Headers;
import io.zeebe.test.util.MsgPackUtil;

public class JobAssert {

  public static void assertJobRecord(Record<JobRecordValue> createJobCmd) {
    Assertions.assertThat(createJobCmd.getValue()).hasRetries(5).hasType("bar");
  }

  public static void assertJobPayload(Record<JobRecordValue> event, String expectedPayload) {
    final byte[] payload = MsgPackUtil.asMsgPackReturnArray(event.getValue().getPayload());
    MsgPackUtil.assertEquality(payload, expectedPayload);
  }

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
