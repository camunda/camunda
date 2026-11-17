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

import java.util.List;

public class CamundaClientClusterVariablesProperties {

  /**
   * Indicates if cluster variable processing is enabled. When {@code true}, variables configured
   * via <code>@ClusterVariables</code> annotations and via the {@code variables} property are
   * applied at startup. When {@code false}, all cluster variable processing is skipped.
   */
  private boolean enabled = true;

  /**
   * Cluster variables to set at startup. Each entry carries a name, a value and optionally
   * metadata, a kind and a tenant ID. Entries without a tenant ID are globally scoped.
   */
  private List<ClusterVariableEntry> variables;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public List<ClusterVariableEntry> getVariables() {
    return variables;
  }

  public void setVariables(final List<ClusterVariableEntry> variables) {
    this.variables = variables;
  }

  /** Returns the configured cluster variables, never {@code null}. */
  public List<ClusterVariableEntry> resolveVariables() {
    return variables != null ? variables : List.of();
  }

  @Override
  public String toString() {
    return "CamundaClientClusterVariablesProperties{"
        + "enabled="
        + enabled
        + ", variables="
        + variables
        + '}';
  }
}
