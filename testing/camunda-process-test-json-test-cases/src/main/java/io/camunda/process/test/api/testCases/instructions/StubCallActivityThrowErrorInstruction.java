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
package io.camunda.process.test.api.testCases.instructions;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.process.test.api.testCases.ElementSelector;
import io.camunda.process.test.api.testCases.TestCaseInstruction;
import io.camunda.process.test.api.testCases.TestCaseInstructionType;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * An instruction to stand in for the process a call activity calls, making it raise a BPMN error.
 *
 * <p>The selected element is a call activity of a process instance created with {@code
 * stubCallActivities}, which starts no called process on its own and waits on a job instead. This
 * instruction throws a BPMN error from that job, so the call activity fails as if its called
 * process had raised the error. To complete it instead, use {@link
 * StubCallActivityCompleteInstruction}.
 */
@Value.Immutable
@JsonDeserialize(builder = ImmutableStubCallActivityThrowErrorInstruction.Builder.class)
public interface StubCallActivityThrowErrorInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.STUB_CALL_ACTIVITY_THROW_ERROR;
  }

  /**
   * The selector to identify the call activity.
   *
   * @return the element selector
   */
  ElementSelector getElementSelector();

  /**
   * The error code to throw.
   *
   * @return the error code
   */
  String getErrorCode();

  /**
   * The error message to throw. Optional.
   *
   * @return the error message or empty if not set
   */
  Optional<String> getErrorMessage();

  /**
   * The variables to set when throwing the error. Defaults to an empty map.
   *
   * @return the variables
   */
  Map<String, Object> getVariables();
}
