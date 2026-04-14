/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.ChecksumGenerator;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.ResourceState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;

public class ExternalConfigurationResourceTransformer implements DeploymentResourceTransformer {

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final ChecksumGenerator checksumGenerator;
  private final ResourceState resourceState;

  public ExternalConfigurationResourceTransformer(
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter,
      final ChecksumGenerator checksumGenerator,
      final ResourceState resourceState) {
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
    this.checksumGenerator = checksumGenerator;
    this.resourceState = resourceState;
  }

  @Override
  public Either<Failure, Void> createMetadata(
      final DeploymentResource resource,
      final DeploymentRecord deployment,
      final DeploymentResourceContext context) {
    return null;
  }

  @Override
  public void writeRecords(final DeploymentResource resource, final DeploymentRecord deployment) {}
}
