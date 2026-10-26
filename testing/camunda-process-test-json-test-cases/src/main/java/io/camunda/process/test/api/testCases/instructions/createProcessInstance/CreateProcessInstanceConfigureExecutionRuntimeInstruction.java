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
package io.camunda.process.test.api.testCases.instructions.createProcessInstance;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

/**
 * An instruction to configure instance-wide execution switches on the process instance.
 *
 * <p>Unlike {@link CreateProcessInstanceTerminateRuntimeInstruction}, which is triggered by an
 * element, this instruction carries switches that apply to the whole instance and has no {@code
 * afterElementId}.
 */
@Value.Immutable
@JsonDeserialize(
    builder = ImmutableCreateProcessInstanceConfigureExecutionRuntimeInstruction.Builder.class)
public interface CreateProcessInstanceConfigureExecutionRuntimeInstruction
    extends CreateProcessInstanceRuntimeInstruction {

  @Value.Default
  @Override
  default String getType() {
    return CreateProcessInstanceRuntimeInstructionType.CONFIGURE_EXECUTION;
  }

  /**
   * Whether to reserve every job the process instance creates for the test, so that no job worker
   * is served them. Defaults to false.
   *
   * <p>The reservation token itself is not part of the test case: it is a runtime value, minted
   * when the test case runs.
   *
   * @return true if the instance's jobs are reserved, false otherwise
   */
  @Value.Default
  default boolean getReserveJobs() {
    return false;
  }

  /**
   * Whether the process instance's call activities start no called process, waiting on a job that
   * stands in for it instead. Defaults to false.
   *
   * @return true if the instance's call activities are stubbed, false otherwise
   */
  @Value.Default
  default boolean getStubCallActivities() {
    return false;
  }

  /**
   * Whether the process instance's timers are kept out of the engine's due-date scheduler, so that
   * none of them fires on its own. Defaults to false.
   *
   * @return true if the instance's timers are held, false otherwise
   */
  @Value.Default
  default boolean getHoldTimers() {
    return false;
  }
}
