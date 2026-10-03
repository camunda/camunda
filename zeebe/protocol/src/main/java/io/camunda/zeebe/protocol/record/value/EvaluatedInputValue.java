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
import java.util.Collections;
import java.util.Set;
import org.immutables.value.Value;

/**
 * An evaluated input of a decision table. It contains details of the input and the value of the
 * evaluated input expression.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableEvaluatedInputValue.Builder.class)
public interface EvaluatedInputValue extends RecordValue {

  /**
   * @return the id of the evaluated input
   */
  String getInputId();

  /**
   * @return the name of the evaluated input
   */
  String getInputName();

  /**
   * @return the value of the evaluated input expression as JSON string
   */
  String getInputValue();

  /**
   * Returns the protection modes declared for this input, decided by the engine when the decision
   * is evaluated: the input carries the configured modes if its expression references a variable
   * whose name matches a configured sensitive-variable pattern.
   *
   * @return the declared protection modes, or an empty set if the input is not protected
   */
  @Value.Default
  default Set<ProtectionMode> getProtectionModes() {
    return Collections.emptySet();
  }

  /**
   * Derived from {@link #getProtectionModes()}. Implementations should annotate this override with
   * {@code @JsonIgnore}, so it is not serialized next to the modes it is derived from.
   *
   * @return {@code true} if the input value must be redacted before it leaves the engine
   */
  default boolean shouldBeRedacted() {
    return getProtectionModes().contains(ProtectionMode.REDACT);
  }
}
