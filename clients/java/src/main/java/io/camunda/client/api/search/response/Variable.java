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
package io.camunda.client.api.search.response;

import io.camunda.client.api.ExperimentalApi;
import io.camunda.client.api.search.enums.ProtectionMode;
import java.util.Collections;
import java.util.Set;

public interface Variable {
  /* The key of the variable */
  Long getVariableKey();

  /* The name of the variable */
  String getName();

  /* The value of the variable */
  String getValue();

  /* The scope key of the variable */
  Long getScopeKey();

  /* The process instance key of the variable */
  Long getProcessInstanceKey();

  /**
   * Returns the key of the root process instance. The root process instance is the top-level
   * ancestor in the process instance hierarchy.
   *
   * <p><strong>Note:</strong> This field is {@code null} for process instance hierarchies created
   * before version 8.9.
   *
   * @return the root process instance key, or {@code null} for data created before version 8.9
   */
  Long getRootProcessInstanceKey();

  /* The tenant id of the variable */
  String getTenantId();

  /**
   * Returns how the variable is treated, for example which protections apply to its value.
   *
   * <p>This is a default method, so that existing implementations of this interface keep compiling.
   *
   * @return the variable metadata, never {@code null}: it has no protection modes if the server did
   *     not return any
   */
  @ExperimentalApi("https://github.com/camunda/camunda/pull/68611")
  default Metadata getMetadata() {
    return Collections::emptySet;
  }

  /* Check if the variable is truncated */
  Boolean isTruncated();

  /**
   * Checks whether the value of the variable was redacted. A redacted value cannot be recovered and
   * is read back as an empty string, so it must not be interpreted as the original value.
   *
   * @return {@code true} if the {@link ProtectionMode#REDACT} protection applies to the variable
   */
  @ExperimentalApi("https://github.com/camunda/camunda/pull/68611")
  default boolean isRedacted() {
    return getMetadata().hasProtectionMode(ProtectionMode.REDACT);
  }

  /**
   * Deserializes the variable's JSON value into the given type.
   *
   * @param type the class to deserialize the value into
   * @param <T> the target type
   * @return the deserialized value
   * @throws IllegalStateException if the variable value is truncated or redacted
   */
  <T> T getValueAsType(Class<T> type);

  /** Describes how a variable is treated, for example which protections apply to its value. */
  @ExperimentalApi("https://github.com/camunda/camunda/pull/68611")
  interface Metadata {

    /**
     * The protections applied to the variable.
     *
     * @return the protection modes, empty if the variable has none, never {@code null}
     */
    Set<ProtectionMode> getProtectionModes();

    /**
     * Checks whether the given protection applies to the variable.
     *
     * @param protectionMode the protection to look for
     * @return {@code true} if the variable has the given protection
     */
    default boolean hasProtectionMode(final ProtectionMode protectionMode) {
      return getProtectionModes().contains(protectionMode);
    }
  }
}
