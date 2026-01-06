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
import io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.dsl.instructions.PublishMessageInstruction;
import io.camunda.process.test.impl.dsl.AssertionFacade;
import io.camunda.process.test.impl.dsl.TestCaseInstructionHandler;
import java.time.Duration;

public class PublishMessageInstructionHandler
    implements TestCaseInstructionHandler<PublishMessageInstruction> {

  @Override
  public void execute(
      final PublishMessageInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final PublishMessageCommandStep3 command;

    if (instruction.getCorrelationKey().isPresent()) {
      command =
          camundaClient
              .newPublishMessageCommand()
              .messageName(instruction.getName())
              .correlationKey(instruction.getCorrelationKey().get());
    } else {
      command =
          camundaClient
              .newPublishMessageCommand()
              .messageName(instruction.getName())
              .withoutCorrelationKey();
    }

    command.variables(instruction.getVariables());

    instruction.getTimeToLive().ifPresent(ttl -> command.timeToLive(Duration.ofMillis(ttl)));

    instruction.getMessageId().ifPresent(command::messageId);

    command.send().join();
  }

  @Override
  public Class<PublishMessageInstruction> getInstructionType() {
    return PublishMessageInstruction.class;
  }
}
