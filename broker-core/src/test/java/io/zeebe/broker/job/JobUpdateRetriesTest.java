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

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.JobRecordValue;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestPartitionClient;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  private TestPartitionClient client;

  @Before
  public void setup() {
    client = apiRule.partition();
  }

  @Test
  public void shouldUpdateRetries() {
    // given
    client.createJob(JOB_TYPE);

    apiRule.openJobSubscription(JOB_TYPE).await();

    final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

    final Map<String, Object> event = subscribedEvent.value();
    event.put("retries", 0);
    client.failJob(subscribedEvent.position(), subscribedEvent.key(), event);

    // when
    final ExecuteCommandResponse response =
        client.updateJobRetries(subscribedEvent.key(), NEW_RETRIES);

    // then
    final SubscribedRecord jobCommand =
        apiRule.partition().receiveFirstJobCommand(JobIntent.UPDATE_RETRIES);

    assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.sourceRecordPosition()).isEqualTo(jobCommand.position());
    assertThat(response.intent()).isEqualTo(JobIntent.RETRIES_UPDATED);

    final Map<String, Object> expectedValue = new HashMap<>(event);
    expectedValue.put("retries", (long) NEW_RETRIES);
    assertThat(response.getValue()).containsAllEntriesOf(expectedValue);

    final Record<JobRecordValue> loggedEvent =
        RecordingExporter.jobRecords(JobIntent.RETRIES_UPDATED).getFirst();

    assertThat(loggedEvent.getValue().getType()).isEqualTo(JOB_TYPE);
  }

  @Test
  public void shouldUpdateRetriesAndRetry() {
    // given
    client.createJob(JOB_TYPE);

    apiRule.openJobSubscription(JOB_TYPE).await();

    final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

    final Map<String, Object> event = subscribedEvent.value();
    event.put("retries", 0);
    client.failJob(subscribedEvent.position(), subscribedEvent.key(), event);

    // when
    final ExecuteCommandResponse response =
        client.updateJobRetries(subscribedEvent.key(), NEW_RETRIES);

    // then
    assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.intent()).isEqualTo(JobIntent.RETRIES_UPDATED);

    // and the job is published again
    final SubscribedRecord republishedEvent = receiveSingleSubscribedEvent();
    assertThat(republishedEvent.key()).isEqualTo(subscribedEvent.key());
    assertThat(republishedEvent.position()).isNotEqualTo(subscribedEvent.position());
    assertThat(republishedEvent.timestamp()).isGreaterThanOrEqualTo(subscribedEvent.timestamp());

    // and the job lifecycle is correct
    apiRule.openTopicSubscription("foo", 0).await();

    final int expectedTopicEvents = 10;

    final List<SubscribedRecord> jobEvents =
        doRepeatedly(
                () ->
                    apiRule
                        .moveMessageStreamToHead()
                        .subscribedEvents()
                        .filter(
                            e ->
                                e.subscriptionType() == SubscriptionType.TOPIC_SUBSCRIPTION
                                    && e.valueType() == ValueType.JOB)
                        .limit(expectedTopicEvents)
                        .collect(Collectors.toList()))
            .until(e -> e.size() == expectedTopicEvents);

    assertThat(jobEvents)
        .extracting(e -> e.recordType(), e -> e.valueType(), e -> e.intent())
        .containsExactly(
            tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.CREATE),
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.CREATED),
            tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.ACTIVATE),
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.ACTIVATED),
            tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.FAIL),
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.FAILED),
            tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.UPDATE_RETRIES),
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.RETRIES_UPDATED),
            tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.ACTIVATE),
            tuple(RecordType.EVENT, ValueType.JOB, JobIntent.ACTIVATED));
  }

  @Test
  public void shouldRejectUpdateRetriesIfJobNotFound() {
    // when
    final ExecuteCommandResponse response = client.updateJobRetries(123, NEW_RETRIES);

    // then
    assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.intent()).isEqualTo(JobIntent.UPDATE_RETRIES);
    assertThat(response.rejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
    assertThat(response.rejectionReason()).isEqualTo("Job is not failed");
  }

  @Test
  public void shouldRejectUpdateRetriesIfJobCompleted() {
    // given
    client.createJob(JOB_TYPE);

    apiRule.openJobSubscription(JOB_TYPE).await();

    final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

    final Map<String, Object> event = subscribedEvent.value();
    client.completeJob(subscribedEvent.position(), subscribedEvent.key(), event);

    // when
    final ExecuteCommandResponse response =
        client.updateJobRetries(subscribedEvent.key(), NEW_RETRIES);

    // then
    assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.intent()).isEqualTo(JobIntent.UPDATE_RETRIES);
    assertThat(response.rejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
    assertThat(response.rejectionReason()).isEqualTo("Job is not failed");
  }

  @Test
  public void shouldRejectUpdateRetriesIfJobActivated() {
    // given
    client.createJob(JOB_TYPE);

    apiRule.openJobSubscription(JOB_TYPE).await();

    final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

    // when
    final ExecuteCommandResponse response =
        client.updateJobRetries(subscribedEvent.key(), NEW_RETRIES);

    // then
    assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.intent()).isEqualTo(JobIntent.UPDATE_RETRIES);
    assertThat(response.rejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
    assertThat(response.rejectionReason()).isEqualTo("Job is not failed");
  }

  @Test
  public void shouldRejectUpdateRetriesIfRetriesZero() {
    // given
    client.createJob(JOB_TYPE);

    apiRule.openJobSubscription(JOB_TYPE).await();

    final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

    final Map<String, Object> event = subscribedEvent.value();
    event.put("retries", 0);
    client.failJob(subscribedEvent.position(), subscribedEvent.key(), event);

    // when
    final ExecuteCommandResponse response = client.updateJobRetries(subscribedEvent.key(), 0);

    // then
    assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.intent()).isEqualTo(JobIntent.UPDATE_RETRIES);
    assertThat(response.rejectionType()).isEqualTo(RejectionType.BAD_VALUE);
    assertThat(response.rejectionReason()).isEqualTo("Job retries must be positive");
  }

  @Test
  public void shouldRejectUpdateRetriesIfRetriesLessThanZero() {
    // given
    client.createJob(JOB_TYPE);

    apiRule.openJobSubscription(JOB_TYPE).await();

    final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

    final Map<String, Object> event = subscribedEvent.value();
    event.put("retries", 0);
    client.failJob(subscribedEvent.position(), subscribedEvent.key(), event);

    // when
    final ExecuteCommandResponse response = client.updateJobRetries(subscribedEvent.key(), -1);

    // then
    assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(response.intent()).isEqualTo(JobIntent.UPDATE_RETRIES);
    assertThat(response.rejectionType()).isEqualTo(RejectionType.BAD_VALUE);
    assertThat(response.rejectionReason()).isEqualTo("Job retries must be positive");
  }

  private SubscribedRecord receiveSingleSubscribedEvent() {
    waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 1);
    return apiRule.subscribedEvents().findFirst().get();
  }
}
