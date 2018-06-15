/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.it.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.Collections;
import java.util.Properties;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.client.ClientProperties;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.events.JobState;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.*;

public class CreateJobTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule(() ->
    {
        final Properties p = new Properties();
        p.setProperty(ClientProperties.REQUEST_TIMEOUT_SEC, "3");
        return p;
    });

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public Timeout testTimeout = Timeout.seconds(15);

    @Test
    public void shouldCreateJob()
    {
        // given
        final JobClient jobClient = clientRule.getClient().topicClient().jobClient();

        // when
        final JobEvent job = jobClient.newCreateCommand()
            .jobType("foo")
            .addCustomHeader("k1", "a")
            .addCustomHeader("k2", "b")
            .send()
            .join();

        // then
        assertThat(job).isNotNull();

        assertThat(job.getKey()).isGreaterThanOrEqualTo(0);
        assertThat(job.getType()).isEqualTo("foo");
        assertThat(job.getState()).isEqualTo(JobState.CREATED);
        assertThat(job.getCustomHeaders()).containsOnly(entry("k1", "a"), entry("k2", "b"));
        assertThat(job.getWorker()).isEmpty();
        assertThat(job.getDeadline()).isNull();
        assertThat(job.getRetries()).isEqualTo(3);

        assertThat(job.getPayload()).isEqualTo("null");
        assertThat(job.getPayloadAsMap()).isNull();
    }

    @Test
    public void shouldCreateJobWithPayload()
    {
        // given
        final JobClient jobClient = clientRule.getClient().topicClient().jobClient();

        // when
        final JobEvent job = jobClient.newCreateCommand()
            .jobType("foo")
            .payload("{\"foo\":\"bar\"}")
            .send()
            .join();

        // then
        assertThat(job.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
        assertThat(job.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
    }

    @Test
    public void shouldCreateJobWithPayloadAsMap()
    {
        // given
        final JobClient jobClient = clientRule.getClient().topicClient().jobClient();

        // when
        final JobEvent job = jobClient.newCreateCommand()
            .jobType("foo")
            .payload(Collections.singletonMap("foo", "bar"))
            .send()
            .join();

        // then
        assertThat(job.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
        assertThat(job.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
    }

    @Test
    public void shouldCreateJobWithPayloadAsObject()
    {
        // given
        final JobClient jobClient = clientRule.getClient().topicClient().jobClient();

        final PayloadObject payload = new PayloadObject();
        payload.foo = "bar";

        // when
        final JobEvent job = jobClient.newCreateCommand()
            .jobType("foo")
            .payload(payload)
            .send()
            .join();

        // then
        assertThat(job.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
        assertThat(job.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
    }

    @Test
    public void shouldFailCreateJobIfTopicNameIsNotValid()
    {
        // given
        final JobClient jobClient = clientRule.getClient().topicClient("unknown-topic").jobClient();

        // then
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Cannot determine target partition for request. " +
                "Request was: [ topic = unknown-topic, partition = any, value type = JOB, command = CREATE ]");

        // when
        jobClient.newCreateCommand()
            .jobType("foo")
            .send()
            .join();
    }

    public static class PayloadObject
    {
        public String foo;
    }
}
