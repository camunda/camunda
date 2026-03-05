/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapArray;
import static java.util.Map.entry;

import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.ChecksumGenerator;
import io.camunda.zeebe.engine.processing.deployment.model.validation.BpmnDeploymentBindingValidator;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.FeatureFlags;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public final class DeploymentTransformer {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;
  private final Map<String, DeploymentResourceTransformer> resourceTransformers;
  private final DeploymentResourceTransformer defaultResourceTransformer;
  private final boolean enableGenericResourceDeployment;
  private final ChecksumGenerator checksumGenerator = new ChecksumGenerator();
  // internal changes during processing
  private RejectionType rejectionType;
  private String rejectionReason;

  public DeploymentTransformer(
      final StateWriter stateWriter,
      final ProcessingState processingState,
      final ExpressionProcessor expressionProcessor,
      final KeyGenerator keyGenerator,
      final FeatureFlags featureFlags,
      final EngineConfiguration config,
      final InstantSource clock,
      final ExpressionLanguageMetrics expressionLanguageMetrics) {

    final var bpmnResourceTransformer =
        new BpmnResourceTransformer(
            keyGenerator,
            stateWriter,
            checksumGenerator,
            processingState.getProcessState(),
            expressionProcessor,
            featureFlags.enableStraightThroughProcessingLoopDetector(),
            config,
            clock,
            expressionLanguageMetrics);
    final var dmnResourceTransformer =
        new DmnResourceTransformer(
            keyGenerator,
            stateWriter,
            checksumGenerator,
            processingState.getDecisionState(),
            config);

    final var formResourceTransformer =
        new FormResourceTransformer(
            keyGenerator, stateWriter, checksumGenerator, processingState.getFormState(), config);

    final var rpaTransformer =
        new RpaTransformer(
            keyGenerator, stateWriter, checksumGenerator, processingState.getResourceState());

    defaultResourceTransformer =
        new DefaultResourceTransformer(
            keyGenerator, stateWriter, checksumGenerator, processingState.getResourceState());

    enableGenericResourceDeployment = featureFlags.enableGenericResourceDeployment();

    resourceTransformers =
        Map.ofEntries(
            entry(".bpmn", bpmnResourceTransformer),
            entry(".xml", bpmnResourceTransformer),
            entry(".dmn", dmnResourceTransformer),
            entry(".form", formResourceTransformer),
            entry(".rpa", rpaTransformer));
  }

  public DirectBuffer getChecksum(final byte[] resource) {
    return wrapArray(checksumGenerator.checksum(resource));
  }

  public Either<Failure, Void> transform(final DeploymentRecord deploymentEvent) {
    final StringBuilder errors = new StringBuilder();
    boolean success = true;

    final Iterator<DeploymentResource> resourceIterator = deploymentEvent.resources().iterator();
    if (!resourceIterator.hasNext()) {
      rejectionType = RejectionType.INVALID_ARGUMENT;
      rejectionReason = "Expected to deploy at least one resource, but none given";

      return Either.left(new Failure(rejectionReason));
    }

    // step 1: only validate the resources and add their metadata to the deployment record (no event
    // records are being written yet)
    final var bpmnResources = new ArrayList<BpmnResource>();
    while (resourceIterator.hasNext()) {
      final DeploymentResource deploymentResource = resourceIterator.next();
      if (isBpmnResource(deploymentResource)) {
        final var context = new BpmnElementsWithDeploymentBinding();
        bpmnResources.add(new BpmnResource(deploymentResource, context));
        success &= createMetadata(deploymentResource, deploymentEvent, context, errors);
      } else {
        success &=
            createMetadata(
                deploymentResource, deploymentEvent, new DeploymentResourceContext() {}, errors);
      }
    }

    // intermediate step (for BPMN resources only): validate process elements that use deployment
    // binding (all linked resources must be part of the current deployment)
    if (success && !bpmnResources.isEmpty()) {
      final var validator = new BpmnDeploymentBindingValidator(deploymentEvent);
      for (final var bpmnResource : bpmnResources) {
        final var validationError = validator.validate(bpmnResource.elements);
        if (validationError != null) {
          success = false;
          errors
              .append("\n'")
              .append(bpmnResource.resource.getResourceName())
              .append("':\n")
              .append(validationError);
        }
      }
    }

    // step 2: update metadata (optionally) and write actual event records.
    // Note: if every resource in the deployment turned out to be a duplicate (hasDuplicatesOnly),
    // no records are written. Otherwise, any resource that was individually marked as a duplicate
    // must also be re-versioned so that all resources in the deployment share the same deployment
    // key and version increment (versioning invariant).
    // Note: the resource ID is used as join key here to find the matching metadata entry, since it
    // is guaranteed to be unique within a deployment (enforced by checkForDuplicateResourceId).
    // The distributed path in DeploymentCreateProcessor re-computes the checksum instead, since
    // the resource ID is not available on DeploymentResource without parsing.
    if (success) {
      for (final DeploymentResource deploymentResource : deploymentEvent.resources()) {
        success &= writeRecords(deploymentResource, deploymentEvent, errors);
      }
    }

    if (!success) {
      rejectionType = RejectionType.INVALID_ARGUMENT;
      rejectionReason =
          String.format(
              "Expected to deploy new resources, but encountered the following errors:%s", errors);

      return Either.left(new Failure(rejectionReason));
    }

    return Either.right(null);
  }

  private boolean isBpmnResource(final DeploymentResource resource) {
    return resource.getResourceName().endsWith(".bpmn")
        || resource.getResourceName().endsWith(".xml");
  }

  private boolean createMetadata(
      final DeploymentResource deploymentResource,
      final DeploymentRecord deploymentEvent,
      final DeploymentResourceContext context,
      final StringBuilder errors) {
    final var resourceName = deploymentResource.getResourceName();
    final var transformer = getResourceTransformer(resourceName);

    try {
      final var result = transformer.createMetadata(deploymentResource, deploymentEvent, context);

      if (result.isRight()) {
        return true;
      } else {
        final var failureMessage = result.getLeft().getMessage();
        errors.append("\n").append(failureMessage);
        return false;
      }

    } catch (final RuntimeException e) {
      handleUnexpectedError(resourceName, e, errors);
    }
    return false;
  }

  private boolean writeRecords(
      final DeploymentResource deploymentResource,
      final DeploymentRecord deploymentEvent,
      final StringBuilder errors) {
    final var resourceName = deploymentResource.getResourceName();
    final var transformer = getResourceTransformer(resourceName);
    try {
      transformer.writeRecords(deploymentResource, deploymentEvent);
      return true;
    } catch (final RuntimeException e) {
      handleUnexpectedError(resourceName, e, errors);
    }
    return false;
  }

  public RejectionType getRejectionType() {
    return rejectionType;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  private DeploymentResourceTransformer getResourceTransformer(final String resourceName) {
    return resourceTransformers.entrySet().stream()
        .filter(entry -> resourceName.endsWith(entry.getKey()))
        .map(Entry::getValue)
        .findFirst()
        .orElseGet(
            () ->
                enableGenericResourceDeployment
                    ? defaultResourceTransformer
                    : new UnsupportedResourceTransformer(resourceName));
  }

  private static void handleUnexpectedError(
      final String resourceName, final RuntimeException exception, final StringBuilder errors) {
    LOG.error("Unexpected error while processing resource '{}'", resourceName, exception);
    errors.append("\n'").append(resourceName).append("': ").append(exception.getMessage());
  }

  /**
   * A transformer that rejects resources with unrecognized file extensions. Used when the
   * {@code enableGenericResourceDeployment} alpha feature flag is disabled.
   */
  private static final class UnsupportedResourceTransformer
      implements DeploymentResourceTransformer {

    private final String resourceName;

    UnsupportedResourceTransformer(final String resourceName) {
      this.resourceName = resourceName;
    }

    @Override
    public Either<Failure, Void> createMetadata(
        final DeploymentResource resource,
        final DeploymentRecord deployment,
        final DeploymentResourceContext context) {
      return Either.left(
          new Failure(
              "'" + resourceName + "': unknown resource type. Generic resource deployment is"
                  + " an alpha feature and must be enabled via"
                  + " camunda.processing.enable-generic-resource-deployment=true"
                  + " (or zeebe.broker.experimental.features.enableGenericResourceDeployment=true"
                  + " for legacy configuration)."));
    }

    @Override
    public void writeRecords(
        final DeploymentResource resource, final DeploymentRecord deployment) {}
  }

  private record BpmnResource(
      DeploymentResource resource, BpmnElementsWithDeploymentBinding elements) {}
}
