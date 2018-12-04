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
package io.zeebe.broker.incident;

import static io.zeebe.broker.incident.IncidentAssert.assertIncidentRecordValue;
import static io.zeebe.exporter.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.IncidentRecordValue;
import io.zeebe.exporter.record.value.JobRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.incident.ErrorType;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.MsgPackUtil;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class JobFailIncidentTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  private static final BpmnModelInstance WORKFLOW_INPUT_MAPPING =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask("failingTask", t -> t.zeebeTaskType("test").zeebeInput("$.foo", "$.foo"))
          .done();

  private static final byte[] PAYLOAD;

  static {
    final DirectBuffer buffer =
        MsgPackUtil.encodeMsgPack(
            p -> {
              p.packMapHeader(1);
              p.packString("foo");
              p.packString("bar");
            });
    PAYLOAD = new byte[buffer.capacity()];
    buffer.getBytes(0, PAYLOAD);
  }

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
    apiRule.waitForPartition(1);
  }

  @Test
  public void shouldCreateIncidentIfJobHasNoRetriesLeft() {
    // given
    testClient.deploy(WORKFLOW_INPUT_MAPPING);

    final long workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);

    // when
    failJobWithNoRetriesLeft();

    // then
    final Record activityEvent =
        testClient.receiveElementInState("failingTask", WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    final Record failedEvent = testClient.receiveFirstJobEvent(JobIntent.FAILED);

    final Record incidentCommand = testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);
    final Record incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    assertThat(incidentCommand.getSourceRecordPosition()).isEqualTo(failedEvent.getPosition());

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertIncidentRecordValue(
        ErrorType.JOB_NO_RETRIES.name(),
        "No more retries left.",
        workflowInstanceKey,
        "failingTask",
        activityEvent.getKey(),
        failedEvent.getKey(),
        incidentEvent);
  }

  @Test
  public void shouldCreateIncidentWithJobErrorMessage() {
    // given
    testClient.deploy(WORKFLOW_INPUT_MAPPING);

    final long workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);

    // when
    createIncidentWithJobWitMessage("failed job");

    // then
    final Record activityEvent =
        testClient.receiveElementInState("failingTask", WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    final Record failedEvent = testClient.receiveFirstJobEvent(JobIntent.FAILED);

    final Record incidentCommand = testClient.receiveFirstIncidentCommand(IncidentIntent.CREATE);
    final Record incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    assertThat(incidentCommand.getSourceRecordPosition()).isEqualTo(failedEvent.getPosition());

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertIncidentRecordValue(
        ErrorType.JOB_NO_RETRIES.name(),
        "failed job",
        workflowInstanceKey,
        "failingTask",
        activityEvent.getKey(),
        failedEvent.getKey(),
        incidentEvent);
  }

  @Test
  public void shouldIncidentContainLastFailedJobErrorMessage() {
    // given
    testClient.deploy(WORKFLOW_INPUT_MAPPING);

    final long workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);

    // when
    failJobWithMessage(1, "first message");
    failJobWithMessage(0, "second message");

    // then
    final Record activityEvent =
        testClient.receiveElementInState("failingTask", WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    final Record failedEvent = testClient.receiveFirstJobEvent(JobIntent.FAILED);

    final Record incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);
    assertIncidentRecordValue(
        ErrorType.JOB_NO_RETRIES.name(),
        "second message",
        workflowInstanceKey,
        "failingTask",
        activityEvent.getKey(),
        failedEvent.getKey(),
        incidentEvent);
  }

  @Test
  public void shouldResolveIncidentIfJobRetriesIncreased() {
    // given
    testClient.deploy(WORKFLOW_INPUT_MAPPING);

    final long workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);

    failJobWithNoRetriesLeft();
    final Record<IncidentRecordValue> incidentCreatedEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    // when
    updateJobRetries();
    testClient.resolveIncident(incidentCreatedEvent.getKey());
    apiRule.activateJobs("test").await();

    // then
    final Record jobEvent = testClient.receiveFirstJobEvent(JobIntent.FAILED);
    final Record activityEvent =
        testClient.receiveElementInState("failingTask", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    Record incidentEvent = testClient.receiveFirstIncidentCommand(IncidentIntent.RESOLVE);
    final long lastPos = incidentEvent.getPosition();
    incidentEvent = testClient.receiveFirstIncidentEvent(IncidentIntent.RESOLVED);

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertThat(incidentEvent.getSourceRecordPosition()).isEqualTo(lastPos);
    assertIncidentRecordValue(
        ErrorType.JOB_NO_RETRIES.name(),
        "No more retries left.",
        workflowInstanceKey,
        "failingTask",
        activityEvent.getKey(),
        jobEvent.getKey(),
        incidentEvent);

    // and the job is published again
    final Record<JobRecordValue> republishedEvent =
        testClient
            .receiveJobs()
            .skipUntil(job -> job.getMetadata().getIntent() == JobIntent.RETRIES_UPDATED)
            .withIntent(JobIntent.ACTIVATED)
            .getFirst();
    assertThat(republishedEvent.getKey()).isEqualTo(jobEvent.getKey());
    assertThat(republishedEvent.getPosition()).isNotEqualTo(jobEvent.getPosition());
    assertThat(republishedEvent.getTimestamp().toEpochMilli())
        .isGreaterThanOrEqualTo(jobEvent.getTimestamp().toEpochMilli());
    assertThat(republishedEvent.getValue()).hasRetries(1);

    // and the job lifecycle is correct
    final List<Record> jobEvents = testClient.receiveJobs().limit(8).collect(Collectors.toList());

    assertThat(jobEvents)
        .extracting(Record::getMetadata)
        .extracting(e -> e.getRecordType(), e -> e.getValueType(), e -> e.getIntent())
        .containsExactly(
            tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.CREATE),
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.CREATED),
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.ACTIVATED),
            tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.FAIL),
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.FAILED),
            tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.UPDATE_RETRIES),
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.RETRIES_UPDATED),
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.ACTIVATED));
  }

  @Test
  public void shouldDeleteIncidentIfJobIsCanceled() {
    // given
    testClient.deploy(WORKFLOW_INPUT_MAPPING);

    final long workflowInstanceKey = testClient.createWorkflowInstance("process", PAYLOAD);

    failJobWithNoRetriesLeft();

    final Record incidentCreatedEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.CREATED);

    // when
    testClient.cancelWorkflowInstance(workflowInstanceKey);

    // then
    final Record<WorkflowInstanceRecordValue> terminatingTask =
        testClient.receiveElementInState("failingTask", WorkflowInstanceIntent.ELEMENT_TERMINATING);
    final Record jobCancelCommand = testClient.receiveFirstJobCommand(JobIntent.CANCEL);
    final Record<IncidentRecordValue> resolvedIncidentEvent =
        testClient.receiveFirstIncidentEvent(IncidentIntent.RESOLVED);

    assertThat(resolvedIncidentEvent.getKey()).isEqualTo(incidentCreatedEvent.getKey());
    assertThat(resolvedIncidentEvent.getSourceRecordPosition())
        .isEqualTo(terminatingTask.getPosition());
    assertThat(jobCancelCommand.getSourceRecordPosition()).isEqualTo(terminatingTask.getPosition());

    assertIncidentRecordValue(
        ErrorType.JOB_NO_RETRIES.name(),
        "No more retries left.",
        workflowInstanceKey,
        "failingTask",
        resolvedIncidentEvent.getValue().getElementInstanceKey(),
        jobCancelCommand.getKey(),
        resolvedIncidentEvent);
  }

  private void failJobWithNoRetriesLeft() {
    apiRule.activateJobs("test").await();

    final Record jobEvent = testClient.receiveFirstJobEvent(JobIntent.ACTIVATED);
    final ExecuteCommandResponse response = testClient.failJob(jobEvent.getKey(), 0);

    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(JobIntent.FAILED);
  }

  private void failJobWithMessage(int retries, String errorMessage) {
    apiRule.activateJobs("test").await();

    final Record jobEvent = testClient.receiveFirstJobEvent(JobIntent.ACTIVATED);
    final ExecuteCommandResponse response =
        testClient.failJobWithMessage(jobEvent.getKey(), retries, errorMessage);

    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(JobIntent.FAILED);
  }

  private void createIncidentWithJobWitMessage(String errorMessage) {
    apiRule.activateJobs("test").await();

    final Record jobEvent = testClient.receiveFirstJobEvent(JobIntent.ACTIVATED);
    final ExecuteCommandResponse response =
        testClient.createJobIncidentWithJobErrorMessage(jobEvent.getKey(), errorMessage);

    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(JobIntent.FAILED);
  }

  private void updateJobRetries() {
    final Record<JobRecordValue> jobEvent = testClient.receiveFirstJobEvent(JobIntent.FAILED);
    final ExecuteCommandResponse response = testClient.updateJobRetries(jobEvent.getKey(), 1);

    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(JobIntent.RETRIES_UPDATED);
  }
}
