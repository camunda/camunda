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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;

public class JobUpdateRetriesTest
{
    private static final String JOB_TYPE = "foo";
    private static final int NEW_RETRIES = 20;

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
    public void shouldUpdateRetries()
    {
        // given
        client.createJob(JOB_TYPE);

        apiRule.openJobSubscription(JOB_TYPE).await();

        final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

        Map<String, Object> event = subscribedEvent.value();
        event.put("retries", 0);
        final ExecuteCommandResponse failResponse = client.failJob(subscribedEvent.key(), event);

        event = failResponse.getValue();
        event.put("retries", NEW_RETRIES);

        // when
        final ExecuteCommandResponse response = client.updateJobRetries(subscribedEvent.key(), event);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.EVENT);
        assertThat(response.intent()).isEqualTo(JobIntent.RETRIES_UPDATED);

        // and the job is published again
        final SubscribedRecord republishedEvent = receiveSingleSubscribedEvent();
        assertThat(republishedEvent.key()).isEqualTo(subscribedEvent.key());
        assertThat(republishedEvent.position()).isNotEqualTo(subscribedEvent.position());

        // and the job lifecycle is correct
        apiRule.openTopicSubscription("foo", 0).await();

        final int expectedTopicEvents = 10;

        final List<SubscribedRecord> jobEvents = doRepeatedly(() -> apiRule
                .moveMessageStreamToHead()
                .subscribedEvents()
                .filter(e -> e.subscriptionType() == SubscriptionType.TOPIC_SUBSCRIPTION &&
                      e.valueType() == ValueType.JOB)
                .limit(expectedTopicEvents)
                .collect(Collectors.toList()))
            .until(e -> e.size() == expectedTopicEvents);

        assertThat(jobEvents).extracting(e -> e.recordType(), e -> e.valueType(), e -> e.intent())
            .containsExactly(
                tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.CREATE),
                tuple(RecordType.EVENT, ValueType.JOB, JobIntent.CREATED),
                tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.LOCK),
                tuple(RecordType.EVENT, ValueType.JOB, JobIntent.LOCKED),
                tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.FAIL),
                tuple(RecordType.EVENT, ValueType.JOB, JobIntent.FAILED),
                tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.UPDATE_RETRIES),
                tuple(RecordType.EVENT, ValueType.JOB, JobIntent.RETRIES_UPDATED),
                tuple(RecordType.COMMAND, ValueType.JOB, JobIntent.LOCK),
                tuple(RecordType.EVENT, ValueType.JOB, JobIntent.LOCKED));
    }

    @Test
    public void shouldRejectUpdateRetriesIfJobNotFound()
    {
        // given
        final Map<String, Object> event = new HashMap<>();

        event.put("retries", NEW_RETRIES);
        event.put("type", JOB_TYPE);

        // when
        final ExecuteCommandResponse response = client.updateJobRetries(123, event);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(JobIntent.UPDATE_RETRIES);
    }

    @Test
    public void shouldRejectUpdateRetriesIfJobCompleted()
    {
        // given
        client.createJob(JOB_TYPE);

        apiRule.openJobSubscription(JOB_TYPE).await();

        final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

        Map<String, Object> event = subscribedEvent.value();
        final ExecuteCommandResponse completeResponse = client.completeJob(subscribedEvent.key(), event);

        event = completeResponse.getValue();
        event.put("retries", NEW_RETRIES);

        // when
        final ExecuteCommandResponse response = client.updateJobRetries(subscribedEvent.key(), event);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(JobIntent.UPDATE_RETRIES);
    }

    @Test
    public void shouldRejectUpdateRetriesIfJobLocked()
    {
        // given
        client.createJob(JOB_TYPE);

        apiRule.openJobSubscription(JOB_TYPE).await();

        final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

        final Map<String, Object> event = subscribedEvent.value();
        event.put("retries", NEW_RETRIES);

        // when
        final ExecuteCommandResponse response = client.updateJobRetries(subscribedEvent.key(), event);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(JobIntent.UPDATE_RETRIES);
    }


    @Test
    public void shouldRejectUpdateRetriesIfRetriesZero()
    {
        // given
        client.createJob(JOB_TYPE);

        apiRule.openJobSubscription(JOB_TYPE).await();

        final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

        Map<String, Object> event = subscribedEvent.value();
        event.put("retries", 0);
        final ExecuteCommandResponse failResponse = client.failJob(subscribedEvent.key(), event);

        event = failResponse.getValue();
        event.put("retries", 0);

        // when
        final ExecuteCommandResponse response = client.updateJobRetries(subscribedEvent.key(), event);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(JobIntent.UPDATE_RETRIES);
    }

    @Test
    public void shouldRejectUpdateRetriesIfRetriesLessThanZero()
    {
        // given
        client.createJob(JOB_TYPE);

        apiRule.openJobSubscription(JOB_TYPE).await();

        final SubscribedRecord subscribedEvent = receiveSingleSubscribedEvent();

        Map<String, Object> event = subscribedEvent.value();
        event.put("retries", 0);
        final ExecuteCommandResponse failResponse = client.failJob(subscribedEvent.key(), event);

        event = failResponse.getValue();
        event.put("retries", -1);

        // when
        final ExecuteCommandResponse response = client.updateJobRetries(subscribedEvent.key(), event);

        // then
        assertThat(response.recordType()).isEqualTo(RecordType.COMMAND_REJECTION);
        assertThat(response.intent()).isEqualTo(JobIntent.UPDATE_RETRIES);
    }

    private SubscribedRecord receiveSingleSubscribedEvent()
    {
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 1);
        return apiRule.subscribedEvents().findFirst().get();
    }
}
