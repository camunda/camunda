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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class JobUpdateRetriesTest {
  private static final String JOB_TYPE = "foo";
  private static final int NEW_RETRIES = 20;

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

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
    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
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
  public void shouldRejectUpdateRetriesIfJobNotFound() {
    // when
    final ExecuteCommandResponse response = client.updateJobRetries(123, NEW_RETRIES);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.getIntent()).isEqualTo(JobIntent.UPDATE_RETRIES);
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
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
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldUpdateRetriesIfJobActivated() {
    // given
    client.createJob(JOB_TYPE);

    apiRule.activateJobs(JOB_TYPE).await();

    final Record<JobRecordValue> jobEvent = client.receiveFirstJobEvent(JobIntent.ACTIVATED);

    // when
    final ExecuteCommandResponse response = client.updateJobRetries(jobEvent.getKey(), NEW_RETRIES);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(JobIntent.RETRIES_UPDATED);

    assertThat(response.getKey()).isEqualTo(jobEvent.getKey());

    final Record<JobRecordValue> loggedEvent =
        client.receiveJobs().withIntent(JobIntent.RETRIES_UPDATED).withType(JOB_TYPE).getFirst();

    assertThat(loggedEvent.getValue().getRetries()).isEqualTo(NEW_RETRIES);
  }

  @Test
  public void shouldUpdateRetriesIfJobCreated() {
    // given
    final long jobKey = client.createJob(JOB_TYPE);

    // when
    final ExecuteCommandResponse response = client.updateJobRetries(jobKey, NEW_RETRIES);

    // then
    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(JobIntent.RETRIES_UPDATED);

    assertThat(response.getKey()).isEqualTo(jobKey);

    final Record<JobRecordValue> updatedRetries =
        client.receiveJobs().withIntent(JobIntent.RETRIES_UPDATED).withType(JOB_TYPE).getFirst();

    assertThat(updatedRetries.getValue().getRetries()).isEqualTo(NEW_RETRIES);
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
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
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
    assertThat(response.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
  }
}
