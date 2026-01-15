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
import io.camunda.process.test.api.dsl.JobSelector;
import io.camunda.process.test.api.dsl.TestCaseInstruction;
import io.camunda.process.test.api.dsl.TestCaseInstructionType;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;

/** An instruction to complete a job of an ad-hoc sub-process. */
@Value.Immutable
@JsonDeserialize(builder = ImmutableCompleteJobAdHocSubProcessInstruction.Builder.class)
public interface CompleteJobAdHocSubProcessInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.COMPLETE_JOB_AD_HOC_SUB_PROCESS;
  }

  /**
   * The selector to identify the job to complete.
   *
   * @return the job selector
   */
  JobSelector getJobSelector();

  /**
   * The variables to complete the job with. Optional.
   *
   * @return the variables
   */
  Map<String, Object> getVariables();

  /**
   * The elements to activate in the ad-hoc sub-process. Optional.
   *
   * @return the list of elements to activate
   */
  List<ActivateElement> getActivateElements();

  /**
   * Whether to cancel remaining instances of the ad-hoc sub-process. Defaults to false.
   *
   * @return true if remaining instances should be canceled, false otherwise
   */
  @Value.Default
  default boolean getCancelRemainingInstances() {
    return false;
  }

  /**
   * Whether the completion condition of the ad-hoc sub-process is fulfilled. Defaults to false.
   *
   * @return true if the completion condition is fulfilled, false otherwise
   */
  @Value.Default
  default boolean getCompletionConditionFulfilled() {
    return false;
  }

  /** An element to activate in the ad-hoc sub-process. */
  @Value.Immutable
  @JsonDeserialize(builder = ImmutableActivateElement.Builder.class)
  interface ActivateElement {

    /**
     * The ID of the element to activate.
     *
     * @return the element ID
     */
    String getElementId();

    /**
     * The variables to set when activating the element. Optional.
     *
     * @return the variables
     */
    Map<String, Object> getVariables();
  }
}
