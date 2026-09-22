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
import org.immutables.value.Value;

/**
 * An instruction to run the process a call activity calls, instead of standing in for it.
 *
 * <p>The selected element is a call activity of a process instance created with {@code
 * stubCallActivities}, which starts no called process on its own and waits on a job instead.
 * Standing in for the called process with {@link StubCallActivityCompleteInstruction} completes
 * that job; this instruction runs the called process for real.
 */
@Value.Immutable
@JsonDeserialize(builder = ImmutableRunCalledProcessInstruction.Builder.class)
public interface RunCalledProcessInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.RUN_CALLED_PROCESS;
  }

  /**
   * The selector to identify the call activity.
   *
   * @return the element selector
   */
  ElementSelector getElementSelector();
}
