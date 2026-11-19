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
package io.camunda.zeebe.protocol.record.value;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import org.immutables.value.Value;

/**
 * Represents the value of a variable source record, containing information about the source of a
 * variable operation.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableVariableSourceValue.Builder.class)
public interface VariableSourceValue extends RecordValue {

  /**
   * @return the type of the variable operation, which tells what wrote the value. See {@link
   *     VariableOperationType} for all types.
   */
  VariableOperationType getType();

  /**
   * @return the key of the element instance that caused the variable operation, or {@code -1} if
   *     the operation has no causing element. For example, this is the job's element instance for a
   *     job completion.
   */
  @Value.Default
  default long getElementInstanceKey() {
    return -1L;
  }

  /**
   * @return the id of the element that caused the variable operation, or an empty string if the
   *     operation has no causing element.
   */
  @Value.Default
  default String getElementId() {
    return "";
  }
}
