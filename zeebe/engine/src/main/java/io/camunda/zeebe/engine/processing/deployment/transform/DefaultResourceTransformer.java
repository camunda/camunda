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
import java.util.Optional;
import org.agrona.DirectBuffer;

/**
 * Default transformer for resources stored in the {@link ResourceState}.
 *
 * <p>Handles the common logic for versioning and writing resource records. By default, the resource
 * name (filename) is used as the resource ID. Subclasses can override {@link
 * #parseResourceInfo(DeploymentResource)} to resolve the resource ID (and optional version tag)
 * from the raw resource content.
 *
 * <h2>Duplicate detection</h2>
 *
 * <p>A resource deployment is considered a <em>duplicate</em> when both the checksum (MD5 of the
 * raw bytes) and the resource ID match the previously deployed version. Only if either value
 * differs will a new version be created.
 *
 * <h2>Filename (resourceName) vs. resource ID</h2>
 *
 * <p>The {@code resourceName} field always reflects the filename used in the deployment command,
 * even for duplicates. This lets callers see which filename was used in each deployment. However,
 * {@code resourceName} is <em>not</em> part of the identity check:
 *
 * <ul>
 *   <li><b>Generic resources</b> (no known extension, e.g. {@code .txt}): the filename <em>is</em>
 *       the resource ID. Renaming the file therefore creates an entirely new, independently
 *       versioned resource.
 *   <li><b>Structured resources</b> (e.g. {@code .rpa}): the resource ID is parsed from the file
 *       content. Renaming the file while keeping the same content and ID is treated as a duplicate
 *       — no new version is created.
 * </ul>
 */
class DefaultResourceTransformer implements DeploymentResourceTransformer {

  private static final int INITIAL_VERSION = 1;

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
   * The default transformer accepts any resource that was not handled by other transformers.
   *
   * @param resource the raw deployment resource
   * @return always returns {@code true} as this is the fallback transformer
   */
  @Override
  public boolean canTransform(final DeploymentResource resource) {
    return true;
  }

  /**
   * Parses the deployment resource to extract the resource identity (ID and optional version tag).
   *
   * <p>The default implementation uses the resource name (filename) as the resource ID. Subclasses
   * can override this method to parse a structured resource ID from the resource content.
   *
   * @param resource the raw deployment resource
   * @return either the parsed {@link ResourceInfo}, or a {@link Failure} if the resource is invalid
   */
  protected Either<Failure, ResourceInfo> parseResourceInfo(final DeploymentResource resource) {
    return Either.right(ResourceInfo.of(resource.getResourceName()));
  }

  @Override
  public final Either<Failure, DeploymentResourceContext> createMetadata(
      final DeploymentResource deploymentResource, final DeploymentRecord deployment) {
    return parseResourceInfo(deploymentResource)
        .map(
            resourceInfo -> {
              addResourceMetadata(resourceInfo, deploymentResource, deployment);
              return DeploymentResourceContext.NONE;
            });
  }

  @Override
  public final void writeRecords(
      final DeploymentResource resource, final DeploymentRecord deployment) {
    final var checksum = checksumGenerator.checksum(resource.getResourceBuffer());
    deployment.resourceMetadata().stream()
        .filter(metadata -> checksum.equals(metadata.getChecksumBuffer()))
        .findFirst()
        .ifPresent(
            metadata -> {
              if (metadata.isDuplicate()) {
                // create new version as the deployment contains at least one other non-duplicate
                // resource and all resources in a deployment should be versioned together
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
      final ResourceMetadataRecord metadata, final DeploymentResource resource) {
    stateWriter.appendFollowUpEvent(
        metadata.getResourceKey(),
        ResourceIntent.CREATED,
        new ResourceRecord().wrap(metadata, resource.getResource()));
  }

  private void addResourceMetadata(
      final ResourceInfo resourceInfo,
      final DeploymentResource deploymentResource,
      final DeploymentRecord deploymentRecord) {
    final ResourceMetadataRecord metadata = deploymentRecord.resourceMetadata().add();
    final DirectBuffer checksum =
        checksumGenerator.checksum(deploymentResource.getResourceBuffer());
    final String resourceId = resourceInfo.id();
    final String tenantId = deploymentRecord.getTenantId();

    metadata.setResourceId(resourceId);
    metadata.setChecksum(checksum);
    metadata.setResourceName(deploymentResource.getResourceName());
    metadata.setTenantId(tenantId);
    resourceInfo.versionTag().ifPresent(metadata::setVersionTag);

    resourceState
        .findLatestResourceById(resourceId, tenantId)
        .ifPresentOrElse(
            latestResource -> {
              if (latestResource.isDuplicateOf(resourceId, checksum)) {
                metadata
                    .setResourceKey(latestResource.getResourceKey())
                    .setVersion(latestResource.getVersion())
                    .setDeploymentKey(latestResource.getDeploymentKey())
                    .setDuplicate(true);
              } else {
                metadata
                    .setResourceKey(keyGenerator.nextKey())
                    .setVersion(resourceState.getNextResourceVersion(resourceId, tenantId))
                    .setDeploymentKey(deploymentRecord.getDeploymentKey());
              }
            },
            () ->
                metadata
                    .setResourceKey(keyGenerator.nextKey())
                    .setVersion(INITIAL_VERSION)
                    .setDeploymentKey(deploymentRecord.getDeploymentKey()));
  }

  /**
   * Holds the parsed identity of a deployment resource: its unique ID and an optional version tag.
   *
   * @param id the resource identifier (must not be null or blank)
   * @param versionTag an optional version tag
   */
  public record ResourceInfo(String id, Optional<String> versionTag) {

    static ResourceInfo of(final String id) {
      return new ResourceInfo(id, Optional.empty());
    }

    static ResourceInfo of(final String id, final String versionTag) {
      return new ResourceInfo(id, Optional.ofNullable(versionTag));
    }
  }
}
