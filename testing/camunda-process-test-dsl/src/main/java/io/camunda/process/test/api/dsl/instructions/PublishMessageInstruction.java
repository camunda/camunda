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
package io.camunda.process.test.api.dsl.instructions;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.process.test.api.dsl.TestCaseInstruction;
import io.camunda.process.test.api.dsl.TestCaseInstructionType;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

/** An instruction to publish a message. */
@Value.Immutable
@JsonDeserialize(builder = ImmutablePublishMessageInstruction.Builder.class)
public interface PublishMessageInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.PUBLISH_MESSAGE;
  }

  /**
   * The name of the message.
   *
   * @return the message name
   */
  String getName();

  /**
   * The correlation key of the message. Optional.
   *
   * @return the correlation key or empty if not set
   */
  Optional<String> getCorrelationKey();

  /**
   * The variables to publish with the message. Optional.
   *
   * @return the variables or an empty map if no variables are set
   */
  Map<String, Object> getVariables();

  /**
   * The time-to-live of the message. Optional.
   *
   * @return the time-to-live in milliseconds or empty if not set
   */
  Optional<Long> getTimeToLive();

  /**
   * The message ID for uniqueness. Optional.
   *
   * @return the message ID or empty if not set
   */
  Optional<String> getMessageId();
}
