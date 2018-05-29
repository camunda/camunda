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

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ControlMessageResponse;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;

public class FailJobTest
{
    private static final String JOB_TYPE = "foo";

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    private TestTopicClient client;

    @Before
    public void setup()
    {
        client = apiRule.topic();
    }

    @Test
    public void shouldFailJob()
    {
        // given
        client.createJob(JOB_TYPE);

        apiRule.openJobSubscription(JOB_TYPE).await();

        final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

        // when
        final ExecuteCommandResponse response = client.failJob(subscribedEvent.key(), subscribedEvent.value());

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(JobIntent.FAILED);

        // and the job is published again
        final SubscribedRecord republishedEvent = receiveSingleSubscribedEvent();
        assertThat(republishedEvent.key()).isEqualTo(subscribedEvent.key());
        assertThat(republishedEvent.position()).isNotEqualTo(subscribedEvent.position());

        // and the job lifecycle is correct
        apiRule.openTopicSubscription("foo", 0).await();

        final int expectedTopicEvents = 8;

        final List<SubscribedRecord> jobEvents = doRepeatedly(() -> apiRule
                .moveMessageStreamToHead()
                .subscribedEvents()
                .filter(e -> e.subscriptionType() == SubscriptionType.TOPIC_SUBSCRIPTION)
                .limit(expectedTopicEvents)
                .collect(Collectors.toList()))
            .until(e -> e.size() == expectedTopicEvents);

        assertThat(jobEvents).extracting(e -> e.recordType(), e -> e.valueType(), e -> e.intent())
            .containsExactly(
                    tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.CREATE),
                    tuple(RecordType.EVENT, ValueType.JOB, JobIntent.CREATED),
                    tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.ACTIVATE),
                    tuple(RecordType.EVENT, ValueType.JOB, JobIntent.ACTIVATED),
                    tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.FAIL),
                    tuple(RecordType.EVENT, ValueType.JOB, JobIntent.FAILED),
                    tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.ACTIVATE),
                    tuple(RecordType.EVENT, ValueType.JOB, JobIntent.ACTIVATED));
    }

    @Test
    public void shouldRejectFailIfJobNotFound()
    {
        // given
        final int key = 123;

        final Map<String, Object> event = new HashMap<>();
        event.put("type", "foo");

        // when
        final ExecuteCommandResponse response = client.failJob(key, event);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.rejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
        assertThat(response.rejectionReason()).isEqualTo("Job is not in state ACTIVATED");
        assertThat(response.intent()).isEqualTo(JobIntent.FAIL);
    }

    @Test
    public void shouldRejectFailIfJobAlreadyFailed()
    {
        // given
        client.createJob(JOB_TYPE);

        final ControlMessageResponse subscriptionResponse = apiRule.openJobSubscription(JOB_TYPE).await();
        final int subscriberKey = (int) subscriptionResponse.getData().get("subscriberKey");

        final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();
        apiRule.closeJobSubscription(subscriberKey).await();

        client.failJob(subscribedEvent.key(), subscribedEvent.value());

        // when
        final ExecuteCommandResponse response = client.failJob(subscribedEvent.key(), subscribedEvent.value());

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.rejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
        assertThat(response.rejectionReason()).isEqualTo("Job is not in state ACTIVATED");
        assertThat(response.intent()).isEqualTo(JobIntent.FAIL);
    }


    @Test
    public void shouldRejectFailIfJobCreated()
    {
        // given
        final ExecuteCommandResponse createResponse = client.createJob(JOB_TYPE);

        // when
        final ExecuteCommandResponse response = client.failJob(createResponse.key(), createResponse.getValue());

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.rejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
        assertThat(response.rejectionReason()).isEqualTo("Job is not in state ACTIVATED");
        assertThat(response.intent()).isEqualTo(JobIntent.FAIL);
    }


    @Test
    public void shouldRejectFailIfJobCompleted()
    {
        // given
        client.createJob(JOB_TYPE);

        apiRule.openJobSubscription(JOB_TYPE).await();

        final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

        client.completeJob(subscribedEvent.key(), subscribedEvent.value());

        // when
        final ExecuteCommandResponse response = client.failJob(subscribedEvent.key(), subscribedEvent.value());

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.rejectionType()).isEqualTo(RejectionType.NOT_APPLICABLE);
        assertThat(response.rejectionReason()).isEqualTo("Job is not in state ACTIVATED");
        assertThat(response.intent()).isEqualTo(JobIntent.FAIL);
    }

    @Test
    public void shouldFailIfNotWorker()
    {
        // given
        final String worker = "peter";

        client.createJob(JOB_TYPE);

        apiRule.createControlMessageRequest()
            .partitionId(apiRule.getDefaultPartitionId())
            .messageType(ControlMessageType.ADD_JOB_SUBSCRIPTION)
            .data()
                .put("jobType", JOB_TYPE)
                .put("timeout", Duration.ofSeconds(30).toMillis())
                .put("worker", worker)
                .put("credits", 10)
                .done()
            .sendAndAwait();

        final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();
        final Map<String, Object> event = subscribedEvent.value();
        event.put("worker", "jan");

        // when
        final ExecuteCommandResponse response = client.failJob(subscribedEvent.key(), event);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(JobIntent.FAILED);
    }

    private SubscribedRecord receiveSingleSubscribedEvent()
    {
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 1);
        return apiRule.subscribedEvents().findFirst().get();
    }
}
