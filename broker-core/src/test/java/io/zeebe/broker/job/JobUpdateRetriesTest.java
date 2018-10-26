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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.JobRecordValue;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class JobUpdateRetriesTest {
  private static final String JOB_TYPE = "foo";
  private static final int NEW_RETRIES = 20;

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient client;

  @Before
  public void setup() {
    client = apiRule.partitionClient();
  }

  @Test
  public void shouldUpdateRetries() {
    // given
    client.createJob(JOB_TYPE);

    apiRule.activateJobs(JOB_TYPE).await();

    final Record<JobRecordValue> jobEvent = client.receiveFirstJobEvent(JobIntent.ACTIVATED);

    client.failJob(jobEvent.getKey(), 0);

    // when
    final ExecuteCommandResponse response = client.updateJobRetries(jobEvent.getKey(), NEW_RETRIES);

    // then
    final Record jobCommand =
        apiRule.partitionClient().receiveFirstJobCommand(JobIntent.UPDATE_RETRIES);

    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getSourceRecordPosition()).isEqualTo(jobCommand.getPosition());
    assertThat(response.getIntent()).isEqualTo(JobIntent.RETRIES_UPDATED);

    assertThat(response.getKey()).isEqualTo(jobEvent.getKey());
    final JobRecordValue jobEventValue = jobEvent.getValue();
    assertThat(response.getValue())
        .contains(
            entry("worker", jobEventValue.getWorker()),
            entry("type", jobEventValue.getType()),
            entry("retries", 20L),
            entry("deadline", jobEventValue.getDeadline().toEpochMilli()));

    final Record<JobRecordValue> loggedEvent =
        client.receiveFirstJobEvent(JobIntent.RETRIES_UPDATED);

    assertThat(loggedEvent.getValue().getType()).isEqualTo(JOB_TYPE);
  }

  @Test
  public void shouldUpdateRetriesAndRetry() {
    // given
    client.createJob(JOB_TYPE);
    apiRule.activateJobs(JOB_TYPE).await();

    final Record jobEvent = client.receiveFirstJobEvent(JobIntent.ACTIVATED);

    client.failJob(jobEvent.getKey(), 0);

    // when
    final ExecuteCommandResponse response = client.updateJobRetries(jobEvent.getKey(), NEW_RETRIES);
    apiRule.activateJobs(JOB_TYPE).await();

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(JobIntent.RETRIES_UPDATED);

    // and the job is published again
    final Record republishedEvent =
        client
            .receiveJobs()
            .skipUntil(job -> job.getMetadata().getIntent() == JobIntent.RETRIES_UPDATED)
            .withIntent(JobIntent.ACTIVATED)
            .getFirst();
    assertThat(republishedEvent.getKey()).isEqualTo(jobEvent.getKey());
    assertThat(republishedEvent.getPosition()).isNotEqualTo(jobEvent.getPosition());
    assertThat(republishedEvent.getTimestamp().toEpochMilli())
        .isGreaterThanOrEqualTo(jobEvent.getTimestamp().toEpochMilli());

    // and the job lifecycle is correct
    final List<Record> jobEvents = client.receiveJobs().limit(8).collect(Collectors.toList());

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
  public void shouldRejectUpdateRetriesIfJobNotFound() {
    // when
    final ExecuteCommandResponse response = client.updateJobRetries(123, NEW_RETRIES);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.getIntent()).isEqualTo(JobIntent.UPDATE_RETRIES);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
    assertThat(response.getRejectionReason()).isEqualTo("Job is not failed");
  }

  @Test
  public void shouldRejectUpdateRetriesIfJobCompleted() {
    // given
    client.createJob(JOB_TYPE);
    apiRule.activateJobs(JOB_TYPE).await();

    final Record<JobRecordValue> jobEvent = client.receiveFirstJobEvent(JobIntent.ACTIVATED);
    client.completeJob(jobEvent.getKey(), "{}");

    // when
    final ExecuteCommandResponse response = client.updateJobRetries(jobEvent.getKey(), NEW_RETRIES);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.getIntent()).isEqualTo(JobIntent.UPDATE_RETRIES);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
    assertThat(response.getRejectionReason()).isEqualTo("Job is not failed");
  }

  @Test
  public void shouldRejectUpdateRetriesIfJobActivated() {
    // given
    client.createJob(JOB_TYPE);

    apiRule.activateJobs(JOB_TYPE).await();

    final Record jobEvent = client.receiveFirstJobEvent(JobIntent.ACTIVATED);

    // when
    final ExecuteCommandResponse response = client.updateJobRetries(jobEvent.getKey(), NEW_RETRIES);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.getIntent()).isEqualTo(JobIntent.UPDATE_RETRIES);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
    assertThat(response.getRejectionReason()).isEqualTo("Job is not failed");
  }

  @Test
  public void shouldRejectUpdateRetriesIfRetriesZero() {
    // given
    client.createJob(JOB_TYPE);

    apiRule.activateJobs(JOB_TYPE).await();

    final Record jobEvent = client.receiveFirstJobEvent(JobIntent.ACTIVATED);

    client.failJob(jobEvent.getKey(), 0);

    // when
    final ExecuteCommandResponse response = client.updateJobRetries(jobEvent.getKey(), 0);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.getIntent()).isEqualTo(JobIntent.UPDATE_RETRIES);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.BAD_VALUE);
    assertThat(response.getRejectionReason()).isEqualTo("Job retries must be positive");
  }

  @Test
  public void shouldRejectUpdateRetriesIfRetriesLessThanZero() {
    // given
    client.createJob(JOB_TYPE);

    apiRule.activateJobs(JOB_TYPE).await();

    final Record jobEvent = client.receiveFirstJobEvent(JobIntent.ACTIVATED);

    client.failJob(jobEvent.getKey(), 0);

    // when
    final ExecuteCommandResponse response = client.updateJobRetries(jobEvent.getKey(), -1);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.getIntent()).isEqualTo(JobIntent.UPDATE_RETRIES);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.BAD_VALUE);
    assertThat(response.getRejectionReason()).isEqualTo("Job retries must be positive");
  }
}
