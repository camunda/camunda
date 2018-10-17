/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.camunda.operate.zeebeimport.record.value.deployment;

import java.util.Arrays;
import java.util.Objects;
import io.zeebe.exporter.record.value.deployment.DeploymentResource;
import io.zeebe.exporter.record.value.deployment.ResourceType;

public class DeploymentResourceImpl implements DeploymentResource {
  private byte[] resource;
  private ResourceType resourceType;
  private String resourceName;

  public DeploymentResourceImpl() {
  }

  @Override
  public byte[] getResource() {
    return resource;
  }

  @Override
  public ResourceType getResourceType() {
    return resourceType;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  public void setResource(byte[] resource) {
    this.resource = resource;
  }

  public void setResourceType(ResourceType resourceType) {
    this.resourceType = resourceType;
  }

  public void setResourceName(String resourceName) {
    this.resourceName = resourceName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DeploymentResourceImpl that = (DeploymentResourceImpl) o;
    return Arrays.equals(resource, that.resource)
        && resourceType == that.resourceType
        && Objects.equals(resourceName, that.resourceName);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(resourceType, resourceName);
    result = 31 * result + Arrays.hashCode(resource);
    return result;
  }

  @Override
  public String toString() {
    return "DeploymentResourceImpl{"
        + "resource="
        + Arrays.toString(resource)
        + ", resourceType="
        + resourceType
        + ", resourceName='"
        + resourceName
        + '\''
        + '}';
  }
}
