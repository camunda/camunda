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
package io.camunda.process.test.api.dsl.instructions.createProcessInstance;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

/** An instruction to execute when starting a process instance. */
@Value.Immutable
@JsonDeserialize(builder = ImmutableCreateProcessInstanceStartInstruction.Builder.class)
public interface CreateProcessInstanceStartInstruction {

  /**
   * The ID of the element to start the process instance at.
   *
   * @return the element id
   */
  String getElementId();
}
