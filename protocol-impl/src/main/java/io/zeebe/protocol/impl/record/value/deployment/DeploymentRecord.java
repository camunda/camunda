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

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.value.ValueArray;

public class DeploymentRecord extends UnpackedObject {

  public static final String RESOURCES = "resources";
  public static final String WORKFLOWS = "workflows";

  private final ArrayProperty<DeploymentResource> resourcesProp =
      new ArrayProperty<>(RESOURCES, new DeploymentResource());

  private final ArrayProperty<Workflow> workflowsProp =
      new ArrayProperty<>(WORKFLOWS, new Workflow());

  public DeploymentRecord() {
    this.declareProperty(resourcesProp).declareProperty(workflowsProp);
  }

  public ValueArray<Workflow> workflows() {
    return workflowsProp;
  }

  public ValueArray<DeploymentResource> resources() {
    return resourcesProp;
  }
}
