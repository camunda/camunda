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
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.util.function.LongSupplier;
import org.agrona.DirectBuffer;

/**
 * A generic resource transformer that handles any file type not covered by a specific transformer.
 * Unlike the {@link RpaTransformer}, this transformer does not require structured content; it uses
 * the resource name (filename) as the resource ID.
 */
class GenericResourceTransformer implements DeploymentResourceTransformer {

  private static final int INITIAL_VERSION = 1;

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final ChecksumGenerator checksumGenerator;
  private final ResourceState resourceState;

  GenericResourceTransformer(
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
      final DeploymentResource deploymentResource,
      final DeploymentRecord deployment,
      final DeploymentResourceContext context) {
    final String resourceId = deploymentResource.getResourceName();
    return checkForDuplicateResourceId(resourceId, deploymentResource, deployment)
        .map(
            noDuplicates -> {
              final ResourceMetadataRecord resourceMetadataRecord =
                  deployment.resourceMetadata().add();
              appendMetadataToResourceRecord(
                  resourceMetadataRecord, deploymentResource, deployment);
              return null;
            });
  }

  @Override
  public void writeRecords(final DeploymentResource resource, final DeploymentRecord deployment) {
    if (deployment.hasDuplicatesOnly()) {
      return;
    }
    final var checksum = checksumGenerator.checksum(resource.getResourceBuffer());
    deployment.resourceMetadata().stream()
        .filter(metadata -> checksum.equals(metadata.getChecksumBuffer()))
        .findFirst()
        .ifPresent(
            metadata -> {
              var key = metadata.getResourceKey();
              if (metadata.isDuplicate()) {
                key = keyGenerator.nextKey();
                metadata
                    .setResourceKey(key)
                    .setVersion(
                        resourceState.getNextResourceVersion(
                            metadata.getResourceId(), metadata.getTenantId()))
                    .setDuplicate(false)
                    .setDeploymentKey(deployment.getDeploymentKey());
              }
              writeResourceRecord(metadata, resource);
            });
  }

  private void writeResourceRecord(
      final ResourceMetadataRecord resourceMetadataRecord, final DeploymentResource resource) {
    stateWriter.appendFollowUpEvent(
        resourceMetadataRecord.getResourceKey(),
        ResourceIntent.CREATED,
        new ResourceRecord().wrap(resourceMetadataRecord, resource.getResource()));
  }

  private void appendMetadataToResourceRecord(
      final ResourceMetadataRecord resourceMetadataRecord,
      final DeploymentResource deploymentResource,
      final DeploymentRecord deploymentRecord) {
    final LongSupplier newResourceKey = keyGenerator::nextKey;
    final DirectBuffer checksum =
        checksumGenerator.checksum(deploymentResource.getResourceBuffer());
    final String resourceId = deploymentResource.getResourceName();
    final String tenantId = deploymentRecord.getTenantId();

    resourceMetadataRecord.setResourceId(resourceId);
    resourceMetadataRecord.setChecksum(checksum);
    resourceMetadataRecord.setResourceName(deploymentResource.getResourceName());
    resourceMetadataRecord.setTenantId(tenantId);

    resourceState
        .findLatestResourceById(resourceId, tenantId)
        .ifPresentOrElse(
            latestResource -> {
              final boolean isDuplicate =
                  latestResource.getChecksum().equals(resourceMetadataRecord.getChecksumBuffer())
                      && latestResource
                          .getResourceName()
                          .equals(resourceMetadataRecord.getResourceNameBuffer());

              if (isDuplicate) {
                final int latestVersion = latestResource.getVersion();
                resourceMetadataRecord
                    .setResourceKey(latestResource.getResourceKey())
                    .setVersion(latestVersion)
                    .setDeploymentKey(latestResource.getDeploymentKey())
                    .setDuplicate(true);
              } else {
                resourceMetadataRecord
                    .setResourceKey(newResourceKey.getAsLong())
                    .setVersion(resourceState.getNextResourceVersion(resourceId, tenantId))
                    .setDeploymentKey(deploymentRecord.getDeploymentKey());
              }
            },
            () ->
                resourceMetadataRecord
                    .setResourceKey(newResourceKey.getAsLong())
                    .setVersion(INITIAL_VERSION)
                    .setDeploymentKey(deploymentRecord.getDeploymentKey()));
  }

  private Either<Failure, ?> checkForDuplicateResourceId(
      final String resourceId,
      final DeploymentResource resource,
      final DeploymentRecord deployment) {
    return deployment.getResourceMetadata().stream()
        .filter(metadata -> metadata.getResourceId().equals(resourceId))
        .findFirst()
        .map(
            dupeResource -> {
              final var failureMessage =
                  String.format(
                      "Expected the resource ids to be unique within a deployment"
                          + " but found a duplicated id '%s' in the resources '%s' and '%s'.",
                      resourceId, dupeResource.getResourceName(), resource.getResourceName());
              return Either.<Failure, Void>left(new Failure(failureMessage));
            })
        .orElse(Either.right(null));
  }
}
