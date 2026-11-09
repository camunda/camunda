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
package io.camunda.client.api.command;

import io.camunda.client.api.ExperimentalApi;
import io.camunda.client.api.search.enums.ProtectionMode;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Describes how a variable is treated when it is written, for example that its value is redacted.
 *
 * <pre>{@code
 * client
 *     .newCreateInstanceCommand()
 *     .bpmnProcessId("order-process")
 *     .latestVersion()
 *     .addVariable("password", "secret", VariableMetadata.redacted())
 *     .send();
 * }</pre>
 *
 * <p>The metadata of a variable replaces the metadata it had before, it is never merged with it.
 * This is also how the engine treats it. A later declaration for the same variable can therefore
 * remove a protection that an earlier one declared. On a command:
 *
 * <ul>
 *   <li>{@code addVariable(...)} and {@code addVariables(...)} replace the metadata of the
 *       variables they set and keep the metadata of the other variables.
 *   <li>{@code variable(...)} and {@code variables(...)} replace all variables and all metadata.
 * </ul>
 *
 * <p>The metadata is keyed by the name of a top-level variable. Protecting a property inside a
 * variable is not supported.
 */
@ExperimentalApi("https://github.com/camunda/camunda/pull/68611")
public final class VariableMetadata {

  private final Set<ProtectionMode> protectionModes;

  private VariableMetadata(final Set<ProtectionMode> protectionModes) {
    final Set<ProtectionMode> copy = EnumSet.noneOf(ProtectionMode.class);
    copy.addAll(protectionModes);
    this.protectionModes = Collections.unmodifiableSet(copy);
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Declares the given protection modes, for example {@code
   * VariableMetadata.of(ProtectionMode.REDACT)}. Without any mode, the variable has no protections.
   *
   * @throws IllegalArgumentException if a mode is {@code null} or {@link
   *     ProtectionMode#UNKNOWN_ENUM_VALUE}
   */
  public static VariableMetadata of(final ProtectionMode... protectionModes) {
    return builder().protectionModes(protectionModes).build();
  }

  /** Like {@link #of(ProtectionMode...)}, for a collection of protection modes. */
  public static VariableMetadata of(final Collection<ProtectionMode> protectionModes) {
    return builder().protectionModes(protectionModes).build();
  }

  /** Redacts the variable value irrecoverably before it is stored. */
  public static VariableMetadata redacted() {
    return of(ProtectionMode.REDACT);
  }

  /**
   * Redacts the values of the given variables irrecoverably before they are stored, for example
   * {@code addVariables(map, VariableMetadata.redacted("password", "token"))}. Same as {@code
   * redacted().forVariables(variableNames)}.
   *
   * @param variableNames the names of the variables to redact
   * @return the metadata keyed by variable name
   */
  public static Map<String, VariableMetadata> redacted(final String... variableNames) {
    return redacted().forVariables(variableNames);
  }

  /**
   * Applies this metadata to each of the given variables, for example {@code variables(map,
   * VariableMetadata.redacted().forVariables("password", "token"))}.
   *
   * @param variableNames the names of the variables this metadata applies to
   * @return the metadata keyed by variable name
   */
  public Map<String, VariableMetadata> forVariables(final String... variableNames) {
    final Map<String, VariableMetadata> result = new LinkedHashMap<>();
    for (final String variableName : variableNames) {
      result.put(variableName, this);
    }
    return Collections.unmodifiableMap(result);
  }

  /** The protections of the variable, empty if it has none. */
  public Set<ProtectionMode> getProtectionModes() {
    return protectionModes;
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof VariableMetadata
        && Objects.equals(protectionModes, ((VariableMetadata) o).protectionModes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(protectionModes);
  }

  @Override
  public String toString() {
    return "VariableMetadata{protectionModes=" + protectionModes + '}';
  }

  public static final class Builder {
    private final Set<ProtectionMode> protectionModes = EnumSet.noneOf(ProtectionMode.class);

    private Builder() {}

    /** Sets the protections of the variable, replacing any set before. */
    public Builder protectionModes(final ProtectionMode... protectionModes) {
      return protectionModes(Arrays.asList(protectionModes));
    }

    /**
     * Sets the protections of the variable, replacing any set before.
     *
     * @throws IllegalArgumentException if a mode is {@code null} or {@link
     *     ProtectionMode#UNKNOWN_ENUM_VALUE}, which only stands for a mode this client does not
     *     know and cannot be sent
     */
    public Builder protectionModes(final Collection<ProtectionMode> protectionModes) {
      for (final ProtectionMode mode : protectionModes) {
        if (mode == null || mode == ProtectionMode.UNKNOWN_ENUM_VALUE) {
          throw new IllegalArgumentException("Cannot declare protection mode " + mode);
        }
      }
      this.protectionModes.clear();
      this.protectionModes.addAll(protectionModes);
      return this;
    }

    public VariableMetadata build() {
      return new VariableMetadata(protectionModes);
    }
  }
}
