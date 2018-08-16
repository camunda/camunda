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
package io.zeebe.gateway;

import static io.zeebe.protocol.Protocol.DEFAULT_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.api.events.JobState;
import io.zeebe.gateway.cmd.ClientException;
import io.zeebe.gateway.util.ClientRule;
import io.zeebe.gateway.util.Events;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.util.TestUtil;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class ZeebeClientTopologyTimeoutTest {

  @Rule public ExpectedException exception = ExpectedException.none();

  public StubBrokerRule broker = new StubBrokerRule();
  public ClientRule clientRule =
      new ClientRule(broker, c -> c.requestTimeout(Duration.ofSeconds(1)));

  @Rule public RuleChain ruleChain = RuleChain.outerRule(broker).around(clientRule);

  @Test
  public void shouldFailRequestIfTopologyCannotBeRefreshed() {
    // given
    broker.onTopologyRequest().doNotRespond();
    broker.onExecuteCommandRequest(ValueType.JOB, JobIntent.COMPLETE).doNotRespond();

    final JobEvent baseEvent = Events.exampleJob();

    final ZeebeClient client = clientRule.getClient();

    // then
    exception.expect(ClientException.class);
    exception.expectMessage(
        "Request timed out (PT1S). "
            + "Request was: [ topic = "
            + DEFAULT_TOPIC
            + ", partition = 1, value type = JOB, command = COMPLETE ]");

    // when
    client.topicClient().jobClient().newCompleteCommand(baseEvent).send().join();
  }

  @Test
  public void shouldRetryTopologyRequestAfterTimeout() {
    // given
    final int topologyTimeoutSeconds = 1;

    broker.onTopologyRequest().doNotRespond();
    broker.jobs().registerCompleteCommand();

    final JobEvent baseEvent = Events.exampleJob();

    final ZeebeClient client = clientRule.getClient();

    // wait for a hanging topology request
    TestUtil.waitUntil(
        () ->
            broker
                    .getReceivedControlMessageRequests()
                    .stream()
                    .filter(r -> r.messageType() == ControlMessageType.REQUEST_TOPOLOGY)
                    .count()
                == 1);

    broker.stubTopologyRequest(); // make topology available
    clientRule
        .getClock()
        .addTime(Duration.ofSeconds(topologyTimeoutSeconds + 1)); // let request time out

    // when making a new request
    final JobEvent jobEvent =
        client.topicClient().jobClient().newCompleteCommand(baseEvent).send().join();

    // then the topology has been refreshed and the request succeeded
    assertThat(jobEvent.getState()).isEqualTo(JobState.COMPLETED);
  }
}
