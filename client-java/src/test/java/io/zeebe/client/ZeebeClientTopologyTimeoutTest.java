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
package io.zeebe.client;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.event.impl.TaskEventImpl;
import io.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.util.Events;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.sched.clock.ControlledActorClock;

public class ZeebeClientTopologyTimeoutTest
{
    @Rule
    public StubBrokerRule broker = new StubBrokerRule();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public AutoCloseableRule closeables = new AutoCloseableRule();

    protected ControlledActorClock clientClock = new ControlledActorClock();


    protected ZeebeClient buildClient()
    {
        final ZeebeClientBuilderImpl config = new ZeebeClientBuilderImpl();
        config.requestTimeout(Duration.ofSeconds(1));

        final ZeebeClient client = new ZeebeClientImpl(config, clientClock);
        closeables.manage(client);
        return client;
    }

    @After
    public void tearDown()
    {
        clientClock.reset();
    }

    @Test
    public void shouldFailRequestIfTopologyCannotBeRefreshed()
    {
        // given
        broker.onTopologyRequest().doNotRespond();
        broker.onExecuteCommandRequest(EventType.TASK_EVENT, "COMPLETE")
            .doNotRespond();

        final TaskEventImpl baseEvent = Events.exampleTask();

        final ZeebeClient client = buildClient();

        // then
        exception.expect(ClientException.class);
        exception.expectMessage("Request timed out (PT1S). " +
                "Request was: [ topic = default-topic, partition = 99, event type = TASK, state = COMPLETE ]");

        // when
        client.tasks().complete(baseEvent).execute();
    }

    @Test
    public void shouldRetryTopologyRequestAfterTimeout()
    {
        // given
        final int topologyTimeoutSeconds = 1;

        broker.onTopologyRequest().doNotRespond();
        broker.onExecuteCommandRequest(EventType.TASK_EVENT, "COMPLETE")
            .respondWith()
            .key(123)
            .value()
              .allOf((r) -> r.getCommand())
              .put("state", "COMPLETED")
              .done()
            .register();
        final TaskEventImpl baseEvent = Events.exampleTask();

        final ZeebeClient client = buildClient();

        // wait for a hanging topology request
        waitUntil(() ->
            broker.getReceivedControlMessageRequests()
                .stream()
                .filter(r -> r.messageType() == ControlMessageType.REQUEST_TOPOLOGY)
                .count() == 1);

        broker.stubTopologyRequest(); // make topology available
        clientClock.addTime(Duration.ofSeconds(topologyTimeoutSeconds + 1)); // let request time out

        // when making a new request
        final TaskEvent response = client.tasks().complete(baseEvent).execute();

        // then the topology has been refreshed and the request succeeded
        assertThat(response.getState()).isEqualTo("COMPLETED");
    }

}
