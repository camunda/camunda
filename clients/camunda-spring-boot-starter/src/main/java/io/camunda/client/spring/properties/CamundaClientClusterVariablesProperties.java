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

import java.util.LinkedHashMap;
import java.util.Map;

public class CamundaClientClusterVariablesProperties {

  /**
   * Indicates if the <code>@ClusterVariables</code> annotation is processed and configured
   * variables are applied.
   */
  private boolean enabled = true;

  /** Cluster variables to set at startup as key-value pairs. */
  private Map<String, Object> variables = new LinkedHashMap<>();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public Map<String, Object> getVariables() {
    return variables;
  }

  public void setVariables(final Map<String, Object> variables) {
    this.variables = variables;
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
