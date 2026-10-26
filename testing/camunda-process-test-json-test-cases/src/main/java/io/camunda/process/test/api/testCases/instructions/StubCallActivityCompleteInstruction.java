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
import org.immutables.value.Value;

/**
 * An instruction to stand in for the process a call activity calls, completing it with the given
 * variables.
 *
 * <p>The selected element is a call activity of a process instance created with {@code
 * stubCallActivities}, which starts no called process on its own and waits on a job instead. This
 * instruction completes that job, so the call activity finishes as if its called process had
 * completed. To run the called process for real instead, use {@link RunCalledProcessInstruction};
 * to make it fail, use {@link StubCallActivityThrowErrorInstruction}.
 */
@Value.Immutable
@JsonDeserialize(builder = ImmutableStubCallActivityCompleteInstruction.Builder.class)
public interface StubCallActivityCompleteInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.STUB_CALL_ACTIVITY_COMPLETE;
  }

  /**
   * The selector to identify the call activity.
   *
   * @return the element selector
   */
  ElementSelector getElementSelector();

  /**
   * The variables to complete the call activity with. Defaults to an empty map.
   *
   * @return the variables
   */
  Map<String, Object> getVariables();
}
