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
package io.camunda.client.spring.properties;

import io.camunda.client.api.search.enums.ClusterVariableKind;
import java.util.Map;

/**
 * A single cluster variable to be set at startup, including optional metadata.
 *
 * <p>Used both by the {@code camunda.client.cluster-variables.variables} property and by <code>
 * @ClusterVariables</code> JSON resources and methods, which may provide a list of such entries
 * instead of a flat name-to-value object.
 */
public class ClusterVariableEntry {

  /** The name of the cluster variable. Required. */
  private String name;

  /** The value of the cluster variable. Required. */
  private Object value;

  /**
   * Optional metadata attached to the cluster variable. Values must be strings or numbers, as
   * enforced by the server.
   */
  private Map<String, Object> metadata;

  /**
   * Optional kind of the cluster variable, defaults to {@code JSON} on the server. Only applied
   * when the variable is created; the kind of an existing variable cannot be changed.
   */
  private ClusterVariableKind kind;

  /**
   * Optional tenant ID. When absent or blank, the variable is globally scoped. Only supported on
   * the {@code camunda.client.cluster-variables.variables} property; for <code>@ClusterVariables
   * </code> the annotation's {@code tenantId} defines the scope.
   */
  private String tenantId;

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(final Object value) {
    this.value = value;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public void setMetadata(final Map<String, Object> metadata) {
    this.metadata = metadata;
  }

  public ClusterVariableKind getKind() {
    return kind;
  }

  public void setKind(final ClusterVariableKind kind) {
    this.kind = kind;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  @Override
  public String toString() {
    return "ClusterVariableEntry{"
        + "name='"
        + name
        + '\''
        + ", value="
        + value
        + ", metadata="
        + metadata
        + ", kind="
        + kind
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
