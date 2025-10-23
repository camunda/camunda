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
 * Represents a record value for a cluster variable in the engine.
 *
 * <p>A cluster variable is a key-value entry stored at the cluster level, and is owned by a
 * specific tenant.
 *
 * <p>The value is immutable and built via {@link ImmutableClusterVariableRecordValue}.
 *
 * <ul>
 *   <li>{@link #getName()} – the unique name of the cluster variable.
 *   <li>{@link #getValue()} – the current value of the cluster variable, as a deserialized object.
 *   <li>{@link #getScope()} – the current scope of the cluster variable.
 * </ul>
 *
 * @see RecordValue;
 * @see TenantOwned
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableClusterVariableRecordValue.Builder.class)
public interface ClusterVariableRecordValue extends RecordValue, TenantOwned {

  /**
   * Returns the unique name of the cluster variable.
   *
   * @return the variable name (never {@code null})
   */
  String getName();

  /**
   * Returns the current value of the variable.
   *
   * <p>The returned object is deserialized based on the stored format. It may be a {@link String},
   * {@link Number}, {@link Boolean}, {@link java.util.Map}, {@link java.util.List}, or another
   * serializable type.
   *
   * @return the variable value
   */
  Object getValue();

  /**
   * Returns the scope in which this variable is defined.
   *
   * <p>The scope determines the visibility and lifecycle of the variable, for example, whether it
   * is global across all tenants or specific to a single tenant.
   *
   * @return the variable scope (never {@code null})
   */
  ClusterVariableScope getScope();
}
