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
import io.zeebe.protocol.record.value.deployment.DeployedProcess;
import io.zeebe.protocol.record.value.deployment.DeploymentResource;
import java.util.List;
import java.util.stream.Stream;

public final class DeploymentRecordStream
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

  public DeploymentRecordStream withDeployedProcesses(
      final List<DeployedProcess> deployedProcesses) {
    return valueFilter(v -> deployedProcesses.equals(v.getDeployedProcesses()));
  }

  public DeploymentRecordStream withDeployedProcess(final DeployedProcess deployedProcess) {
    return valueFilter(v -> v.getDeployedProcesses().contains(deployedProcess));
  }
}
