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

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;

import java.util.Collections;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.RecordingJobHandler;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.events.JobState;
import io.zeebe.client.cmd.BrokerErrorException;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import org.junit.*;
import org.junit.rules.*;

public class CompleteJobTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public Timeout testTimeout = Timeout.seconds(15);

    private JobEvent jobEvent;

    @Before
    public void init()
    {
        clientRule
            .getJobClient()
            .newCreateCommand()
            .jobType("test")
            .send()
            .join();

        final RecordingJobHandler jobHandler = new RecordingJobHandler();
        clientRule
            .getJobClient()
            .newWorker()
            .jobType("test")
            .handler(jobHandler)
            .open();

        waitUntil(() -> !jobHandler.getHandledJobs().isEmpty());
        jobEvent = jobHandler.getHandledJobs().get(0);
    }

    @Test
    public void shouldCompleteJobWithoutPayload()
    {
        // when
        final JobEvent job = clientRule
            .getJobClient()
            .newCompleteCommand(jobEvent)
            .send()
            .join();

        // then
        assertThat(job.getState()).isEqualTo(JobState.COMPLETED);
        assertThat(job.getPayload()).isEqualTo("{}");
        assertThat(job.getPayloadAsMap()).isEmpty();
    }

    @Test
    public void shouldCompleteJobNullPayload()
    {
        // when
        final JobEvent job = clientRule
            .getJobClient()
            .newCompleteCommand(jobEvent)
            .payload("null")
            .send()
            .join();

        // then
        assertThat(job.getState()).isEqualTo(JobState.COMPLETED);
        assertThat(job.getPayload()).isEqualTo("{}");
        assertThat(job.getPayloadAsMap()).isEmpty();
    }


    @Test
    public void shouldCompleteJobWithPayload()
    {
        // when
        final JobEvent job = clientRule
            .getJobClient()
            .newCompleteCommand(jobEvent)
            .payload("{\"foo\":\"bar\"}")
            .send()
            .join();

        // then
        assertThat(job.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
        assertThat(job.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
    }

    @Test
    public void shouldThrowExceptionOnCompleteJobWithInvalidPayload()
    {
        // when
        final Throwable throwable = catchThrowable(() -> clientRule
            .getJobClient()
            .newCompleteCommand(jobEvent)
            .payload("[]")
            .send()
            .join());

        // then
        assertThat(throwable).isInstanceOf(BrokerErrorException.class);
        assertThat(throwable.getMessage()).contains("Could not read property 'payload'.");
        assertThat(throwable.getMessage()).contains("Document has invalid format. On root level an object is only allowed.");
    }

    @Test
    public void shouldCompleteJobWithPayloadAsMap()
    {
        // when
        final JobEvent job = clientRule
            .getJobClient()
            .newCompleteCommand(jobEvent)
            .payload(Collections.singletonMap("foo", "bar"))
            .send()
            .join();

        // then
        assertThat(job.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
        assertThat(job.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
    }

    @Test
    public void shouldCompleteJobWithPayloadAsObject()
    {
        final PayloadObject payload = new PayloadObject();
        payload.foo = "bar";

        // when
        final JobEvent job = clientRule
            .getJobClient()
            .newCompleteCommand(jobEvent)
            .payload(payload)
            .send()
            .join();

        // then
        assertThat(job.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
        assertThat(job.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
    }

    @Test
    public void shouldProvideReasonInExceptionMessageOnRejection()
    {
        // given
        final JobClient jobClient = clientRule.getClient().topicClient().jobClient();

        final JobEvent job = jobClient.newCreateCommand()
            .jobType("bar")
            .send()
            .join();

        // then
        thrown.expect(ClientCommandRejectedException.class);
        thrown.expectMessage("Command (COMPLETE) for event with key " +
                job.getKey() +
                " was rejected. It is not applicable in the current state. " +
                "Job is not in state: ACTIVATED, TIMED_OUT");

        // when
        jobClient.newCompleteCommand(job)
            .send()
            .join();
    }

    public static class PayloadObject
    {
        public String foo;
    }
}
