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
package io.camunda.client.annotation.value;

import java.util.List;
import java.util.Objects;

public final class DeploymentValue {
  private final List<String> resources;
  private final String tenantId;

  public DeploymentValue(final List<String> resources, final String tenantId) {
    this.resources = resources;
    this.tenantId = tenantId;
  }

  public List<String> getResources() {
    return resources;
  }

  public String getTenantId() {
    return tenantId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(resources, tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DeploymentValue that = (DeploymentValue) o;
    return Objects.equals(resources, that.resources) && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "DeploymentValue{" + "resources=" + resources + ", tenantId='" + tenantId + '\'' + '}';
  }
}
