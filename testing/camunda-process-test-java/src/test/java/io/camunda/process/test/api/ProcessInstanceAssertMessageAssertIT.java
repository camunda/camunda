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
            .variable("eventId", 1)
            .send()
            .join();

    // then: waiting for first message
    CamundaAssert.assertThatProcessInstance(processInstance)
        .isActive()
        .hasVariable("eventId", 1)
        .isWaitingForMessage("message_awaiting")
        .isNotWaitingForMessage("message_await_before_parallel")
        .isNotWaitingForMessage("message_parallel_a")
        .isNotWaitingForMessage("message_parallel_b");

    // when sending a first message
    client
        .newPublishMessageCommand()
        .messageName("message_awaiting")
        .correlationKey("1")
        .send()
        .join();

    // then has two message subscriptions
    CamundaAssert.assertThatProcessInstance(processInstance)
        .isActive()
        .hasVariable("eventId", 1)
        .isWaitingForMessage("message_awaiting")
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

    // then has all four message subscriptions
    CamundaAssert.assertThatProcessInstance(processInstance)
        .isActive()
        .hasVariable("eventId", 1)
        .isWaitingForMessage("message_awaiting")
        .isWaitingForMessage("message_await_before_parallel")
        .isWaitingForMessage("message_parallel_a")
        .isWaitingForMessage("message_parallel_b");
  }
}
