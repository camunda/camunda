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
package io.zeebe.broker.engine.job;

import static io.zeebe.protocol.intent.JobIntent.ACTIVATED;
import static io.zeebe.protocol.intent.JobIntent.FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.RecordMetadata;
import io.zeebe.exporter.api.record.value.JobBatchRecordValue;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobBatchIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class FailJobTest {
  private static final String JOB_TYPE = "foo";

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient client;

  @Before
  public void setup() {
    client = apiRule.partitionClient();
  }

  @Test
  public void shouldFail() {
    // given
    client.createJob(JOB_TYPE);
    apiRule.activateJobs(JOB_TYPE).await();
    final Record<JobRecordValue> jobEvent = client.receiveFirstJobEvent(ACTIVATED);
    final int retries = 23;

    // when
    final ExecuteCommandResponse response = client.failJob(jobEvent.getKey(), retries);

    // then
    final JobRecordValue jobEventValue = jobEvent.getValue();
    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(FAILED);
    assertThat(response.getValue())
        .contains(
            entry("worker", jobEventValue.getWorker()),
            entry("type", jobEventValue.getType()),
            entry("retries", (long) retries),
            entry("deadline", jobEventValue.getDeadline().toEpochMilli()));

    final Record<JobRecordValue> loggedEvent = RecordingExporter.jobRecords(FAILED).getFirst();

    assertThat(loggedEvent.getValue().getType()).isEqualTo(JOB_TYPE);
  }

  @Test
  public void shouldFailWithMessage() {
    // given
    client.createJob(JOB_TYPE);
    apiRule.activateJobs(JOB_TYPE).await();
    final Record<JobRecordValue> jobEvent = client.receiveFirstJobEvent(ACTIVATED);
    final int retries = 23;

    // when
    final ExecuteCommandResponse response =
        client.failJobWithMessage(jobEvent.getKey(), retries, "failed job");

    // then
    final JobRecordValue jobEventValue = jobEvent.getValue();
    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(FAILED);
    assertThat(response.getValue())
        .contains(
            entry("worker", jobEventValue.getWorker()),
            entry("type", jobEventValue.getType()),
            entry("retries", (long) retries),
            entry("deadline", jobEventValue.getDeadline().toEpochMilli()),
            entry("errorMessage", "failed job"));

    final Record<JobRecordValue> loggedEvent = RecordingExporter.jobRecords(FAILED).getFirst();

    assertThat(loggedEvent.getValue().getType()).isEqualTo(JOB_TYPE);
    assertThat(loggedEvent.getValue().getErrorMessage()).isEqualTo("failed job");
  }

  @Test
  public void shouldFailJobAndRetry() {
    // given
    client.createJob(JOB_TYPE);

    apiRule.activateJobs(JOB_TYPE).await();
    final Record jobEvent = client.receiveFirstJobEvent(JobIntent.ACTIVATED);

    // when
    final ExecuteCommandResponse response = client.failJob(jobEvent.getKey(), 3);
    apiRule.activateJobs(JOB_TYPE).await();

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(FAILED);

    // and the job is published again
    final Record republishedEvent =
        RecordingExporter.jobRecords()
            .skipUntil(j -> j.getMetadata().getIntent() == FAILED)
            .withIntent(ACTIVATED)
            .getFirst();

    assertThat(republishedEvent.getKey()).isEqualTo(jobEvent.getKey());
    assertThat(republishedEvent.getPosition()).isNotEqualTo(jobEvent.getPosition());

    // and the job lifecycle is correct
    final List<Record> jobEvents = client.receiveJobs().limit(6).collect(Collectors.toList());
    assertThat(jobEvents)
        .extracting(Record::getMetadata)
        .extracting(
            RecordMetadata::getRecordType, RecordMetadata::getValueType, RecordMetadata::getIntent)
        .containsExactly(
            tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.CREATE),
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.CREATED),
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.ACTIVATED),
            tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.FAIL),
            tuple(RecordType.EVENT, ValueType.JOB, FAILED),
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.ACTIVATED));

    final List<Record<JobBatchRecordValue>> jobActivateCommands =
        client.receiveJobBatchs().limit(4).collect(Collectors.toList());

    assertThat(jobActivateCommands)
        .extracting(Record::getMetadata)
        .extracting(
            RecordMetadata::getRecordType, RecordMetadata::getValueType, RecordMetadata::getIntent)
        .containsExactly(
            tuple(RecordType.COMMAND, ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE),
            tuple(RecordType.EVENT, ValueType.JOB_BATCH, JobBatchIntent.ACTIVATED),
            tuple(RecordType.COMMAND, ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE),
            tuple(RecordType.EVENT, ValueType.JOB_BATCH, JobBatchIntent.ACTIVATED));
  }

  @Test
  public void shouldRejectFailIfJobNotFound() {
    // given
    final int key = 123;

    // when
    final ExecuteCommandResponse response = client.failJob(key, 3);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(response.getIntent()).isEqualTo(JobIntent.FAIL);
  }

  @Test
  public void shouldRejectFailIfJobAlreadyFailed() {
    // given
    client.createJob(JOB_TYPE);
    apiRule.activateJobs(JOB_TYPE).await();
    final Record jobEvent = client.receiveFirstJobEvent(JobIntent.ACTIVATED);

    client.failJob(jobEvent.getKey(), 0);

    // when
    final ExecuteCommandResponse response = client.failJob(jobEvent.getKey(), 3);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(response.getRejectionReason()).contains("is marked as failed");
    assertThat(response.getIntent()).isEqualTo(JobIntent.FAIL);
  }

  @Test
  public void shouldRejectFailIfJobCreated() {
    // given
    final long jobKey = client.createJob(JOB_TYPE);

    // when
    final ExecuteCommandResponse response = client.failJob(jobKey, 3);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(response.getRejectionReason()).contains("must be activated first");
    assertThat(response.getIntent()).isEqualTo(JobIntent.FAIL);
  }

  @Test
  public void shouldRejectFailIfJobCompleted() {
    // given
    client.createJob(JOB_TYPE);
    apiRule.activateJobs(JOB_TYPE).await();

    final Record<JobRecordValue> jobEvent = client.receiveFirstJobEvent(JobIntent.ACTIVATED);
    client.completeJob(jobEvent.getKey(), jobEvent.getValue().getVariables());

    // when
    final ExecuteCommandResponse response = client.failJob(jobEvent.getKey(), 3);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(response.getIntent()).isEqualTo(JobIntent.FAIL);
  }
}
