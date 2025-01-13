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
package io.camunda.client.impl.response;

import io.camunda.client.api.response.Resource;
import java.util.Objects;

public class ResourceImpl implements Resource {

  private final String resourceId;
  private final long resourceKey;
  private final int version;
  private final String resourceName;
  private final String tenantId;

  public ResourceImpl(
      final String resourceId,
      final long resourceKey,
      final Integer version,
      final String resourceName,
      final String tenantId) {
    this.resourceId = resourceId;
    this.resourceKey = resourceKey;
    this.version = version;
    this.resourceName = resourceName;
    this.tenantId = tenantId;
  }

  @Override
  public String getResourceId() {
    return resourceId;
  }

  @Override
  public long getResourceKey() {
    return resourceKey;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(resourceId, resourceKey, version, resourceName, tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ResourceImpl resource = (ResourceImpl) o;
    return resourceKey == resource.resourceKey
        && version == resource.version
        && Objects.equals(resourceId, resource.resourceId)
        && Objects.equals(resourceName, resource.resourceName)
        && Objects.equals(tenantId, resource.tenantId);
  }

  @Override
  public String toString() {
    return "ResourceImpl{"
        + "resourceId='"
        + resourceId
        + '\''
        + ", resourceKey="
        + resourceKey
        + ", version="
        + version
        + ", resourceName='"
        + resourceName
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
