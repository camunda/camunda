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
 * Declares how a variable is treated when it is written, for example which protections apply to its
 * value.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * client
 *     .newCreateInstanceCommand()
 *     .bpmnProcessId("order-process")
 *     .latestVersion()
 *     .addVariable("password", "secret", VariableMetadataInput.redacted())
 *     .send();
 * }</pre>
 *
 * <h2>What you can declare</h2>
 *
 * Only what you declare is sent. The protection modes can be declared in three ways:
 *
 * <ul>
 *   <li><b>Some modes</b>, for example {@code VariableMetadataInput.of(ProtectionMode.REDACT)}: the
 *       variable is protected with these modes.
 *   <li><b>Nothing</b>, for example {@code VariableMetadataInput.builder().build()}: no metadata is
 *       sent, so the variable keeps the protections it already has.
 *   <li><b>An empty set of modes</b>, for example {@code VariableMetadataInput.of()}: the variable
 *       loses its protections.
 * </ul>
 *
 * <h2>Declarations add up</h2>
 *
 * Declaring more protections does not replace earlier ones when you use the builder, {@link
 * #and(VariableMetadataInput)}, {@code addVariable(...)} or {@code addVariables(...)}. For example,
 * redacting a variable in one call and masking it in another leaves it with both protections.
 *
 * <p>{@code variable(...)} and {@code variables(...)} work differently: like the variables
 * themselves, they replace everything set on the command before, including the metadata. A
 * replacement can therefore drop protections declared earlier, for example when it declares an
 * empty set of modes or no metadata at all. Do not rely on protections surviving a replacement.
 *
 * <h2>Creating and applying</h2>
 *
 * Instances are immutable. Use {@link #redacted()} or {@link #of(ProtectionMode...)} for the common
 * cases, and {@link #forVariables(String...)} to apply the same metadata to several variables by
 * name.
 */
public final class VariableMetadataInput {

  /** {@code null} when no protection modes were declared, which is different from an empty set. */
  private final Set<ProtectionMode> protectionModes;

  private VariableMetadataInput(final Set<ProtectionMode> protectionModes) {
    this.protectionModes = protectionModes == null ? null : unmodifiableCopyOf(protectionModes);
  }

  private static Set<ProtectionMode> unmodifiableCopyOf(final Set<ProtectionMode> modes) {
    final Set<ProtectionMode> copy = EnumSet.noneOf(ProtectionMode.class);
    copy.addAll(modes);
    return Collections.unmodifiableSet(copy);
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Declares exactly the given protection modes, for example {@code
   * VariableMetadataInput.of(ProtectionMode.REDACT)}. Declaring no modes at all is explicit and
   * removes the protections the variable has.
   */
  public static VariableMetadataInput of(final ProtectionMode... protectionModes) {
    return builder().protectionModes(protectionModes).build();
  }

  /** Like {@link #of(ProtectionMode...)}, for a collection of protection modes. */
  public static VariableMetadataInput of(final Collection<ProtectionMode> protectionModes) {
    return builder().protectionModes(protectionModes).build();
  }

  /** Declares that the variable value is irrecoverably redacted before it is stored. */
  public static VariableMetadataInput redacted() {
    return of(ProtectionMode.REDACT);
  }

  /**
   * Combines this metadata with the other one, keeping the protections of both, for example {@code
   * redacted().and(masked())}. Declaring nothing is neutral: combined with metadata that declares
   * something, the result declares just that.
   *
   * @param other the metadata to add to this one
   * @return new metadata that declares the protections of both, this one if the other declares
   *     nothing
   */
  public VariableMetadataInput and(final VariableMetadataInput other) {
    Objects.requireNonNull(other, "other");
    if (other.protectionModes == null) {
      return this;
    }
    if (protectionModes == null) {
      return other;
    }
    final Set<ProtectionMode> combined = EnumSet.noneOf(ProtectionMode.class);
    combined.addAll(protectionModes);
    combined.addAll(other.protectionModes);
    return new VariableMetadataInput(combined);
  }

  /**
   * Applies this metadata to each of the given variables, for the commands that take the variables
   * metadata keyed by variable name, for example {@code variables(map,
   * VariableMetadataInput.redacted().forVariables("password", "token"))}.
   *
   * @param variableNames the names of the variables this metadata applies to
   * @return the metadata keyed by variable name
   */
  public Map<String, VariableMetadataInput> forVariables(final String... variableNames) {
    final Map<String, VariableMetadataInput> result = new LinkedHashMap<>();
    for (final String variableName : variableNames) {
      result.put(variableName, this);
    }
    return Collections.unmodifiableMap(result);
  }

  /**
   * The protections declared for the variable.
   *
   * @return the declared protections, an empty set if the protections are to be removed, or {@code
   *     null} if none were declared and the variable keeps the protections it has
   */
  public Set<ProtectionMode> getProtectionModes() {
    return protectionModes;
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof VariableMetadataInput
        && Objects.equals(protectionModes, ((VariableMetadataInput) o).protectionModes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(protectionModes);
  }

  @Override
  public String toString() {
    return "VariableMetadataInput{protectionModes=" + protectionModes + '}';
  }

  public static final class Builder {
    private Set<ProtectionMode> protectionModes;

    private Builder() {}

    /** Declares the protections of the variable, in addition to any declared before. */
    public Builder protectionModes(final ProtectionMode... protectionModes) {
      return protectionModes(Arrays.asList(protectionModes));
    }

    /** Declares the protections of the variable, in addition to any declared before. */
    public Builder protectionModes(final Collection<ProtectionMode> protectionModes) {
      if (this.protectionModes == null) {
        this.protectionModes = EnumSet.noneOf(ProtectionMode.class);
      }
      this.protectionModes.addAll(protectionModes);
      return this;
    }

    public VariableMetadataInput build() {
      return new VariableMetadataInput(protectionModes);
    }
  }
}
