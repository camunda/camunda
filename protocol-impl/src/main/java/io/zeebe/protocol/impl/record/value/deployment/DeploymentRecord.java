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

import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.DeploymentRecordValue;
import io.zeebe.protocol.record.value.deployment.DeployedWorkflow;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;

public class DeploymentRecord extends UnifiedRecordValue implements DeploymentRecordValue {

  public static final String RESOURCES = "resources";
  public static final String WORKFLOWS = "deployedWorkflows";

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

  @Override
  public List<io.zeebe.protocol.record.value.deployment.DeploymentResource> getResources() {

    final List<io.zeebe.protocol.record.value.deployment.DeploymentResource> resources =
        new ArrayList<>();

    final Iterator<DeploymentResource> iterator = resourcesProp.iterator();
    iterator.forEachRemaining(resources::add);

    return resources;
  }

  @Override
  public List<DeployedWorkflow> getDeployedWorkflows() {
    final List<DeployedWorkflow> workflows = new ArrayList<>();

    final Iterator<Workflow> iterator = workflowsProp.iterator();
    while (iterator.hasNext()) {
      final Workflow workflow = iterator.next();

      final byte[] bytes = new byte[workflow.getLength()];
      final UnsafeBuffer copyBuffer = new UnsafeBuffer(bytes);
      workflow.write(copyBuffer, 0);

      final Workflow copiedWorkflow = new Workflow();
      copiedWorkflow.wrap(copyBuffer);
      workflows.add(copiedWorkflow);
    }

    return workflows;
  }
}
