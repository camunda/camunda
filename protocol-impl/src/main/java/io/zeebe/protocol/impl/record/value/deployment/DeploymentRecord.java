/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.protocol.impl.record.value.deployment;

import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.value.DeploymentRecordValue;
import io.zeebe.protocol.record.value.deployment.DeployedWorkflow;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;

public final class DeploymentRecord extends UnifiedRecordValue implements DeploymentRecordValue {

  public static final String RESOURCES = "resources";
  public static final String WORKFLOWS = "deployedWorkflows";

  private final ArrayProperty<DeploymentResource> resourcesProp =
      new ArrayProperty<>(RESOURCES, new DeploymentResource());

  private final ArrayProperty<WorkflowRecord> workflowsProp =
      new ArrayProperty<>(WORKFLOWS, new WorkflowRecord());

  public DeploymentRecord() {
    declareProperty(resourcesProp).declareProperty(workflowsProp);
  }

  public ValueArray<WorkflowRecord> workflows() {
    return workflowsProp;
  }

  public ValueArray<DeploymentResource> resources() {
    return resourcesProp;
  }

  @Override
  public List<io.zeebe.protocol.record.value.deployment.DeploymentResource> getResources() {
    final List<io.zeebe.protocol.record.value.deployment.DeploymentResource> resources =
        new ArrayList<>();

    for (final DeploymentResource resource : resourcesProp) {
      final byte[] bytes = new byte[resource.getLength()];
      final UnsafeBuffer copyBuffer = new UnsafeBuffer(bytes);
      resource.write(copyBuffer, 0);

      final DeploymentResource copiedResource = new DeploymentResource();
      copiedResource.wrap(copyBuffer);
      resources.add(copiedResource);
    }

    return resources;
  }

  @Override
  public List<DeployedWorkflow> getDeployedWorkflows() {
    final List<DeployedWorkflow> workflows = new ArrayList<>();

    for (final WorkflowRecord workflowRecord : workflowsProp) {
      final byte[] bytes = new byte[workflowRecord.getLength()];
      final UnsafeBuffer copyBuffer = new UnsafeBuffer(bytes);
      workflowRecord.write(copyBuffer, 0);

      final WorkflowRecord copiedWorkflowRecord = new WorkflowRecord();
      copiedWorkflowRecord.wrap(copyBuffer);
      workflows.add(copiedWorkflowRecord);
    }

    return workflows;
  }
}
