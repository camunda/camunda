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
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.agrona.DirectBuffer;

/**
 * Default transformer for resources stored in the {@link ResourceState}.
 *
 * <p>Handles the common logic for versioning and writing resource records. By default, the resource
 * name (filename) is used as the resource ID. Subclasses can override {@link
 * #parseResourceId(DeploymentResource)} to resolve the resource ID (and optional version tag) from
 * the raw resource content.
 */
class DefaultResourceTransformer implements DeploymentResourceTransformer {

  private static final int INITIAL_VERSION = 1;
  private static final Either<Failure, Void> SUCCESS = Either.right(null);

  protected final KeyGenerator keyGenerator;
  protected final StateWriter stateWriter;
  protected final ChecksumGenerator checksumGenerator;
  protected final ResourceState resourceState;

  DefaultResourceTransformer(
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter,
      final ChecksumGenerator checksumGenerator,
      final ResourceState resourceState) {
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
    this.checksumGenerator = checksumGenerator;
    this.resourceState = resourceState;
  }

  /**
   * Parses the deployment resource to extract the resource identity (ID and optional version tag).
   *
   * <p>The default implementation uses the resource name (filename) as the resource ID. Subclasses
   * can override this method to parse a structured resource ID from the resource content.
   *
   * @param resource the raw deployment resource
   * @return either the parsed {@link ResourceId}, or a {@link Failure} if the resource is invalid
   */
  protected Either<Failure, ResourceId> parseResourceId(final DeploymentResource resource) {
    return Either.right(ResourceId.of(resource.getResourceName()));
  }

  @Override
  public final Either<Failure, Void> createMetadata(
      final DeploymentResource deploymentResource,
      final DeploymentRecord deployment,
      final DeploymentResourceContext context) {
    return parseResourceId(deploymentResource)
        .flatMap(
            resourceId ->
                checkForDuplicateResourceId(resourceId.id(), deploymentResource, deployment)
                    .flatMap(
                        ignored -> {
                          final ResourceMetadataRecord resourceMetadataRecord =
                              deployment.resourceMetadata().add();
                          appendMetadataToResourceRecord(
                              resourceMetadataRecord, resourceId, deploymentResource, deployment);
                          return SUCCESS;
                        }));
  }

  @Override
  public final void writeRecords(
      final DeploymentResource resource, final DeploymentRecord deployment) {
    if (deployment.hasDuplicatesOnly()) {
      return;
    }
    final var checksum = checksumGenerator.checksum(resource.getResourceBuffer());
    deployment.resourceMetadata().stream()
        .filter(metadata -> checksum.equals(metadata.getChecksumBuffer()))
        .findFirst()
        .ifPresent(
            metadata -> {
              if (metadata.isDuplicate()) {
                metadata
                    .setResourceKey(keyGenerator.nextKey())
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

  private Either<Failure, Void> checkForDuplicateResourceId(
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
        .orElse(SUCCESS);
  }

  private void appendMetadataToResourceRecord(
      final ResourceMetadataRecord resourceMetadataRecord,
      final ResourceId resourceId,
      final DeploymentResource deploymentResource,
      final DeploymentRecord deploymentRecord) {
    final LongSupplier newResourceKey = keyGenerator::nextKey;
    final DirectBuffer checksum =
        checksumGenerator.checksum(deploymentResource.getResourceBuffer());
    final String id = resourceId.id();
    final String tenantId = deploymentRecord.getTenantId();

    resourceMetadataRecord.setResourceId(id);
    resourceMetadataRecord.setChecksum(checksum);
    resourceMetadataRecord.setResourceName(deploymentResource.getResourceName());
    resourceMetadataRecord.setTenantId(tenantId);
    resourceId.versionTag().ifPresent(resourceMetadataRecord::setVersionTag);

    resourceState
        .findLatestResourceById(id, tenantId)
        .ifPresentOrElse(
            latestResource -> {
              if (resourceMetadataRecord.isDuplicateOf(
                  BufferUtil.bufferAsArray(latestResource.getChecksum()),
                  BufferUtil.bufferAsString(latestResource.getResourceName()))) {
                resourceMetadataRecord
                    .setResourceKey(latestResource.getResourceKey())
                    .setVersion(latestResource.getVersion())
                    .setDeploymentKey(latestResource.getDeploymentKey())
                    .setDuplicate(true);
              } else {
                resourceMetadataRecord
                    .setResourceKey(newResourceKey.getAsLong())
                    .setVersion(resourceState.getNextResourceVersion(id, tenantId))
                    .setDeploymentKey(deploymentRecord.getDeploymentKey());
              }
            },
            () ->
                resourceMetadataRecord
                    .setResourceKey(newResourceKey.getAsLong())
                    .setVersion(INITIAL_VERSION)
                    .setDeploymentKey(deploymentRecord.getDeploymentKey()));
  }

  /**
   * Holds the parsed identity of a deployment resource: its unique ID and an optional version tag.
   *
   * @param id the resource identifier (must not be null or blank)
   * @param versionTag an optional version tag
   */
  record ResourceId(String id, Optional<String> versionTag) {

    static ResourceId of(final String id) {
      return new ResourceId(id, Optional.empty());
    }

    static ResourceId of(final String id, final String versionTag) {
      return new ResourceId(id, Optional.ofNullable(versionTag));
    }
  }
}
