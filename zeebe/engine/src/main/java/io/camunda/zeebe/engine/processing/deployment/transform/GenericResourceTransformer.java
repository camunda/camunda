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
import io.camunda.zeebe.engine.processing.deployment.transform.AbstractResourceTransformer.ResourceId;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.ResourceState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;

/**
 * A generic resource transformer that handles any file type not covered by a specific transformer.
 * Unlike the {@link RpaTransformer}, this transformer does not require structured content; it uses
 * the resource name (filename) as the resource ID.
 */
class GenericResourceTransformer extends AbstractResourceTransformer {

  GenericResourceTransformer(
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter,
      final ChecksumGenerator checksumGenerator,
      final ResourceState resourceState) {
    super(keyGenerator, stateWriter, checksumGenerator, resourceState);
  }

  @Override
  protected Either<Failure, ResourceId> parseResourceId(final DeploymentResource resource) {
    return Either.right(ResourceId.of(resource.getResourceName()));
  }
}
