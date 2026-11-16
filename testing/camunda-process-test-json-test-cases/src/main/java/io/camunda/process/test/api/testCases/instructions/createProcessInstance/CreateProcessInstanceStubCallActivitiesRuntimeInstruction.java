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
import java.util.List;
import org.immutables.value.Value;

/**
 * An instruction to stub the process instance's call activities that call one of the listed BPMN
 * process ids.
 *
 * <p>A stubbed call activity starts no called process: it activates and waits on a job that stands
 * in for the called process. Unlike {@link CreateProcessInstanceTerminateRuntimeInstruction}, which
 * is triggered by an element, this instruction applies to the whole instance tree and has no {@code
 * afterElementId}.
 */
@Value.Immutable
@JsonDeserialize(
    builder = ImmutableCreateProcessInstanceStubCallActivitiesRuntimeInstruction.Builder.class)
public interface CreateProcessInstanceStubCallActivitiesRuntimeInstruction
    extends CreateProcessInstanceRuntimeInstruction {

  @Value.Default
  @Override
  default String getType() {
    return CreateProcessInstanceRuntimeInstructionType.STUB_CALL_ACTIVITIES;
  }

  /**
   * The BPMN process ids whose call activities are stubbed. A call activity is stubbed when the
   * process id it calls is in this list.
   *
   * @return the called-process ids to stub
   */
  List<String> getProcessIds();
}
