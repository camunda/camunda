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
package io.camunda.zeebe.protocol.record;

import org.immutables.value.Value;

public interface JsonSerializable {

  /**
   * @return a JSON marshaled representation
   * @throws UnsupportedOperationException if the implementation does not support it; in that case,
   *     you may try using a library like Jackson with our {@link io.camunda.zeebe.protocol.jackson}
   *     module.
   */
  @Value.NonAttribute
  default String toJson() {
    throw new UnsupportedOperationException(
        "Failed to serialize value to JSON; this implementation does not support it out of the box. "
            + " You may want to try with the protocol-jackson module.");
  }
}
