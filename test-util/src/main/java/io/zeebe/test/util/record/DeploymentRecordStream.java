/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.record;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.value.DeploymentRecordValue;
import io.zeebe.protocol.record.value.deployment.DeployedWorkflow;
import io.zeebe.protocol.record.value.deployment.DeploymentResource;
import java.util.List;
import java.util.stream.Stream;

public class DeploymentRecordStream
    extends ExporterRecordStream<DeploymentRecordValue, DeploymentRecordStream> {

  public DeploymentRecordStream(final Stream<Record<DeploymentRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected DeploymentRecordStream supply(
      final Stream<Record<DeploymentRecordValue>> wrappedStream) {
    return new DeploymentRecordStream(wrappedStream);
  }

  public DeploymentRecordStream withResources(final List<DeploymentResource> resources) {
    return valueFilter(v -> resources.equals(v.getResources()));
  }

  public DeploymentRecordStream withResource(final DeploymentResource resource) {
    return valueFilter(v -> v.getResources().contains(resource));
  }

  public DeploymentRecordStream withDeployedWorkflows(
      final List<DeployedWorkflow> deployedWorkflows) {
    return valueFilter(v -> deployedWorkflows.equals(v.getDeployedWorkflows()));
  }

  public DeploymentRecordStream withDeployedWorkflow(final DeployedWorkflow deployedWorkflow) {
    return valueFilter(v -> v.getDeployedWorkflows().contains(deployedWorkflow));
  }
}
