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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

public class CamundaClientClusterVariablesProperties {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaClientClusterVariablesProperties.class);

  /**
   * Indicates if cluster variable processing is enabled. When {@code true}, variables configured
   * via <code>@ClusterVariables</code> annotations and via the {@code variables} property are
   * applied at startup. The deprecated {@code global} and {@code tenant} properties are also
   * applied for compatibility. When {@code false}, all cluster variable processing is skipped.
   */
  private boolean enabled = true;

  /**
   * Cluster variables to set at startup. Each entry carries a name, a value and optionally
   * metadata, a kind and a tenant ID. Entries without a tenant ID are globally scoped.
   */
  private List<ClusterVariableEntry> variables;

  /**
   * Globally-scoped cluster variables to set at startup as key-value pairs.
   *
   * @deprecated use {@link #variables} instead, which also supports metadata.
   */
  @Deprecated private Map<String, Object> global;

  /**
   * Tenant-scoped cluster variables to set at startup, keyed by tenant ID.
   *
   * @deprecated use {@link #variables} instead, which also supports metadata.
   */
  @Deprecated private Map<String, Map<String, Object>> tenant;

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

  /**
   * Returns the configured cluster variables, converting the deprecated {@code global}/{@code
   * tenant} maps into entries when the {@code variables} list is not set. When both shapes are
   * configured, {@code variables} wins and the deprecated maps are ignored.
   */
  public List<ClusterVariableEntry> resolveVariables() {
    final boolean hasGlobal = global != null && !global.isEmpty();
    final boolean hasTenant = tenant != null && !tenant.isEmpty();

    if (variables != null && !variables.isEmpty()) {
      if (hasGlobal || hasTenant) {
        LOGGER.warn(
            "Ignoring 'camunda.client.cluster-variables.global' and "
                + "'camunda.client.cluster-variables.tenant' because "
                + "'camunda.client.cluster-variables.variables' is configured");
      }
      return variables;
    }

    final List<ClusterVariableEntry> resolved = new ArrayList<>();
    if (hasGlobal) {
      LOGGER.warn(
          "The property 'camunda.client.cluster-variables.global' is deprecated, "
              + "use 'camunda.client.cluster-variables.variables' instead");
      global.forEach((name, value) -> resolved.add(entry(name, value, null)));
    }
    if (hasTenant) {
      LOGGER.warn(
          "The property 'camunda.client.cluster-variables.tenant' is deprecated, "
              + "use 'camunda.client.cluster-variables.variables' instead");
      tenant.forEach(
          (tenantId, tenantVariables) -> {
            if (tenantId == null || tenantId.isBlank()) {
              throw new IllegalArgumentException(
                  "Invalid tenant ID in 'camunda.client.cluster-variables.tenant': tenant ID must not be null or blank");
            }
            if (tenantVariables != null) {
              tenantVariables.forEach((name, value) -> resolved.add(entry(name, value, tenantId)));
            }
          });
    }
    return resolved;
  }

  private static ClusterVariableEntry entry(
      final String name, final Object value, final String tenantId) {
    final ClusterVariableEntry entry = new ClusterVariableEntry();
    entry.setName(name);
    entry.setValue(value);
    entry.setTenantId(tenantId);
    return entry;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(
      reason = "does not support metadata",
      replacement = "camunda.client.cluster-variables.variables")
  public Map<String, Object> getGlobal() {
    return global;
  }

  @Deprecated
  public void setGlobal(final Map<String, Object> global) {
    this.global = global;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(
      reason = "does not support metadata",
      replacement = "camunda.client.cluster-variables.variables")
  public Map<String, Map<String, Object>> getTenant() {
    return tenant;
  }

  @Deprecated
  public void setTenant(final Map<String, Map<String, Object>> tenant) {
    this.tenant = tenant;
  }

  @Override
  public String toString() {
    return "CamundaClientClusterVariablesProperties{"
        + "enabled="
        + enabled
        + ", variables="
        + variables
        + ", global="
        + global
        + ", tenant="
        + tenant
        + '}';
  }
}
