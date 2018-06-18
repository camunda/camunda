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
package io.zeebe.client.impl.command;

import io.zeebe.client.api.commands.DeploymentResource;
import io.zeebe.client.api.commands.ResourceType;
import java.nio.charset.StandardCharsets;

public class DeploymentResourceImpl implements DeploymentResource {
  private byte[] resource;
  private ResourceType resourceType;
  private String resourceName;

  @Override
  public byte[] getResource() {
    return resource;
  }

  public void setResource(byte[] resource) {
    this.resource = resource;
  }

  @Override
  public ResourceType getResourceType() {
    return resourceType;
  }

  public void setResourceType(ResourceType resourceType) {
    this.resourceType = resourceType;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  public void setResourceName(String resourceName) {
    this.resourceName = resourceName;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("DeploymentResourceImpl [resourceName=");
    builder.append(resourceName);
    builder.append(", resourceType=");
    builder.append(resourceType);
    builder.append(", resource=");
    builder.append(new String(resource, StandardCharsets.UTF_8));
    builder.append("]");
    return builder.toString();
  }
}
