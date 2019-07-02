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
package io.zeebe.protocol.impl.record.value.deployment;

import static io.zeebe.util.buffer.BufferUtil.wrapArray;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.BinaryProperty;
import io.zeebe.msgpack.property.EnumProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.record.value.deployment.ResourceType;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class DeploymentResource extends UnpackedObject
    implements io.zeebe.protocol.record.value.deployment.DeploymentResource {
  private final BinaryProperty resourceProp = new BinaryProperty("resource");
  private final EnumProperty<ResourceType> resourceTypeProp =
      new EnumProperty<>("resourceType", ResourceType.class, ResourceType.BPMN_XML);
  private final StringProperty resourceNameProp = new StringProperty("resourceName", "resource");

  public DeploymentResource() {
    this.declareProperty(resourceTypeProp)
        .declareProperty(resourceNameProp)
        // the resource property is updated while iterating over the deployment record
        // when a YAML workflow is transformed into its XML representation
        // therefore the resource properties has to be the last one written to the buffer
        // as otherwise it will potentially override other information
        // https://github.com/zeebe-io/zeebe/issues/1931
        .declareProperty(resourceProp);
  }

  public static ResourceType getResourceType(String resourceName) {
    resourceName = resourceName.toLowerCase();

    if (resourceName.endsWith(".yaml")) {
      return ResourceType.YAML_WORKFLOW;
    } else if (resourceName.endsWith(".bpmn") || resourceName.endsWith(".bpmn20.xml")) {
      return ResourceType.BPMN_XML;
    } else {
      throw new RuntimeException(
          String.format(
              "Expected to resolve type of resource '%s', but could not; should be a .bpmn or .yaml file",
              resourceName));
    }
  }

  @Override
  public byte[] getResource() {
    return BufferUtil.bufferAsArray(resourceProp.getValue());
  }

  public ResourceType getResourceType() {
    return resourceTypeProp.getValue();
  }

  @Override
  public String getResourceName() {
    return BufferUtil.bufferAsString(resourceNameProp.getValue());
  }

  @JsonIgnore
  public DirectBuffer getResourceBuffer() {
    return resourceProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getResourceNameBuffer() {
    return resourceNameProp.getValue();
  }

  @Override
  @JsonIgnore
  public int getLength() {
    return super.getLength();
  }

  @Override
  @JsonIgnore
  public int getEncodedLength() {
    return super.getEncodedLength();
  }

  public DeploymentResource setResource(byte[] resource) {
    return setResource(wrapArray(resource));
  }

  public DeploymentResource setResource(DirectBuffer resource) {
    return setResource(resource, 0, resource.capacity());
  }

  public DeploymentResource setResource(DirectBuffer resource, int offset, int length) {
    this.resourceProp.setValue(resource, offset, length);
    return this;
  }

  public DeploymentResource setResourceName(String resourceName) {
    this.resourceNameProp.setValue(resourceName);
    return this;
  }

  public DeploymentResource setResourceName(DirectBuffer resourceName) {
    this.resourceNameProp.setValue(resourceName);
    return this;
  }

  public DeploymentResource setResourceType(ResourceType resourceType) {
    this.resourceTypeProp.setValue(resourceType);
    return this;
  }
}
