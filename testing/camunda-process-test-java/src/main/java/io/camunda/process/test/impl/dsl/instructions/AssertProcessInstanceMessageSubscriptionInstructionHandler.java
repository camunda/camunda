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
package io.camunda.process.test.impl.dsl.instructions;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.dsl.MessageSelector;
import io.camunda.process.test.api.dsl.instructions.AssertProcessInstanceMessageSubscriptionInstruction;
import io.camunda.process.test.api.dsl.instructions.assertProcessInstanceMessageSubscription.MessageSubscriptionState;
import io.camunda.process.test.impl.dsl.AssertionFacade;
import io.camunda.process.test.impl.dsl.TestCaseInstructionHandler;

public class AssertProcessInstanceMessageSubscriptionInstructionHandler
    implements TestCaseInstructionHandler<AssertProcessInstanceMessageSubscriptionInstruction> {

  @Override
  public void execute(
      final AssertProcessInstanceMessageSubscriptionInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final ProcessInstanceSelector processInstanceSelector =
        InstructionSelectorFactory.buildProcessInstanceSelector(
            instruction.getProcessInstanceSelector());

    final ProcessInstanceAssert processInstanceAssert =
        assertionFacade.assertThatProcessInstance(processInstanceSelector);

    final MessageSelector messageSelector = instruction.getMessageSelector();
    final String messageName = messageSelector.getMessageName();
    final MessageSubscriptionState state = instruction.getState();

    if (messageSelector.getCorrelationKey().isPresent()) {
      final String correlationKey = messageSelector.getCorrelationKey().get();
      assertMessageSubscription(processInstanceAssert, messageName, correlationKey, state);
    } else {
      assertMessageSubscription(processInstanceAssert, messageName, state);
    }
  }

  @Override
  public Class<AssertProcessInstanceMessageSubscriptionInstruction> getInstructionType() {
    return AssertProcessInstanceMessageSubscriptionInstruction.class;
  }

  private static void assertMessageSubscription(
      final ProcessInstanceAssert processInstanceAssert,
      final String messageName,
      final MessageSubscriptionState state) {
    switch (state) {
      case IS_WAITING:
        processInstanceAssert.isWaitingForMessage(messageName);
        break;
      case IS_NOT_WAITING:
        processInstanceAssert.isNotWaitingForMessage(messageName);
        break;
      case IS_CORRELATED:
        processInstanceAssert.hasCorrelatedMessage(messageName);
        break;
      default:
        throw new IllegalArgumentException("Unsupported message subscription state: " + state);
    }
  }

  private static void assertMessageSubscription(
      final ProcessInstanceAssert processInstanceAssert,
      final String messageName,
      final String correlationKey,
      final MessageSubscriptionState state) {
    switch (state) {
      case IS_WAITING:
        processInstanceAssert.isWaitingForMessage(messageName, correlationKey);
        break;
      case IS_NOT_WAITING:
        processInstanceAssert.isNotWaitingForMessage(messageName, correlationKey);
        break;
      case IS_CORRELATED:
        processInstanceAssert.hasCorrelatedMessage(messageName, correlationKey);
        break;
      default:
        throw new IllegalArgumentException("Unsupported message subscription state: " + state);
    }
  }
}
