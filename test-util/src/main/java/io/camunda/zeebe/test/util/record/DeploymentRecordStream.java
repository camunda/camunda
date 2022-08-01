/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
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

  public DeploymentRecordStream withDeployedProcesses(final List<Process> processes) {
    return valueFilter(v -> processes.equals(v.getProcessesMetadata()));
  }

  public DeploymentRecordStream withDeployedProcess(final Process process) {
    return valueFilter(v -> v.getProcessesMetadata().contains(process));
  }

  public DeploymentRecordStream withResourceName(final String resourceName) {
    return valueFilter(
        v ->
            v.getResources().stream()
                .map(DeploymentResource::getResourceName)
                .anyMatch(resourceName::equals));
  }
}
