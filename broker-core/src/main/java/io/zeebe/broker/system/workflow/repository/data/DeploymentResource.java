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
package io.zeebe.broker.system.workflow.repository.data;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.*;
import org.agrona.DirectBuffer;

public class DeploymentResource extends UnpackedObject {
  private final BinaryProperty resourceProp = new BinaryProperty("resource");
  private final EnumProperty<ResourceType> resourceTypeProp =
      new EnumProperty<ResourceType>("resourceType", ResourceType.class, ResourceType.BPMN_XML);
  private final StringProperty resourceNameProp = new StringProperty("resourceName", "resource");

  public DeploymentResource() {
    this.declareProperty(resourceProp)
        .declareProperty(resourceTypeProp)
        .declareProperty(resourceNameProp);
  }

  public DirectBuffer getResource() {
    return resourceProp.getValue();
  }

  public DeploymentResource setResource(DirectBuffer resource) {
    return setResource(resource, 0, resource.capacity());
  }

  public DeploymentResource setResource(DirectBuffer resource, int offset, int length) {
    this.resourceProp.setValue(resource, offset, length);
    return this;
  }

  public ResourceType getResourceType() {
    return resourceTypeProp.getValue();
  }

  public DeploymentResource setResourceType(ResourceType resourceType) {
    this.resourceTypeProp.setValue(resourceType);
    return this;
  }

  public DirectBuffer getResourceName() {
    return resourceNameProp.getValue();
  }

  public DeploymentResource setResourceName(DirectBuffer resourceName) {
    this.resourceNameProp.setValue(resourceName);
    return this;
  }
}
