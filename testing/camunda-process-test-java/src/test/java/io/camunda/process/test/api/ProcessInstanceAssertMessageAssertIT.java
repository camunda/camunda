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
package io.camunda.process.test.api;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
public class ProcessInstanceAssertMessageAssertIT {

  // to be injected
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @Test
  public void processInstanceAssertMessageAssert() {
    // given
    client
        .newDeployResourceCommand()
        .addResourceFromClasspath("processInstanceAssertMessageAssertIT/await-message.bpmn")
        .send()
        .join();

    // when starting the process and waiting for the first message
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process_message_subscriptions")
            .latestVersion()
            .variable("eventId", "1")
            .send()
            .join();

    // then: waiting for first message
    CamundaAssert.assertThatProcessInstance(processInstance)
        .isActive()
        .hasVariable("eventId", "1")
        .isWaitingForMessage("message_awaiting")
        .isNotWaitingForMessage("message_await_before_parallel")
        .isNotWaitingForMessage("message_parallel_a")
        .isNotWaitingForMessage("message_parallel_b");

    // correlate first message
    client
        .newCorrelateMessageCommand()
        .messageName("message_awaiting")
        .correlationKey("1")
        .send()
        .join();

    // then has two message subscriptions
    CamundaAssert.assertThatProcessInstance(processInstance)
        .isActive()
        .hasVariable("eventId", "1")
        .hasCorrelatedMessage("message_awaiting", "1")
        .isWaitingForMessage("message_await_before_parallel")
        .isNotWaitingForMessage("message_parallel_a")
        .isNotWaitingForMessage("message_parallel_b");

    // when sending a second message
    client
        .newPublishMessageCommand()
        .messageName("message_await_before_parallel")
        .correlationKey("1")
        .send()
        .join();

    // then we have two correlated messages
    CamundaAssert.assertThatProcessInstance(processInstance)
        .isActive()
        .hasVariable("eventId", "1")
        .hasCorrelatedMessage("message_awaiting")
        .hasCorrelatedMessage("message_await_before_parallel")
        // Same assertion, but with the correlation key to ensure both methods work
        .hasCorrelatedMessage("message_awaiting", "1")
        .hasCorrelatedMessage("message_await_before_parallel", "1")
        .isWaitingForMessage("message_parallel_a")
        .isWaitingForMessage("message_parallel_b");

    // when sending the final two messages
    client
        .newPublishMessageCommand()
        .messageName("message_parallel_a")
        .correlationKey("1")
        .send()
        .join();
    client
        .newPublishMessageCommand()
        .messageName("message_parallel_b")
        .correlationKey("1")
        .send()
        .join();

    // then all messages are correlated
    CamundaAssert.assertThatProcessInstance(processInstance)
        .isCompleted()
        .hasVariable("eventId", "1")
        .hasCorrelatedMessage("message_awaiting")
        .hasCorrelatedMessage("message_await_before_parallel")
        .hasCorrelatedMessage("message_awaiting", "1")
        .hasCorrelatedMessage("message_await_before_parallel", "1")
        .hasCorrelatedMessage("message_parallel_a")
        .hasCorrelatedMessage("message_parallel_b")
        .hasCorrelatedMessage("message_parallel_a", "1")
        .hasCorrelatedMessage("message_parallel_b", "1");
  }
}
