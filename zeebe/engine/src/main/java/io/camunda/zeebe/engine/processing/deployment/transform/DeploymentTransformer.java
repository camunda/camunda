/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapArray;

import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.metrics.ProcessDefinitionMetrics;
import io.camunda.zeebe.engine.processing.clusterversion.ClusterVersionFeatures;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.ChecksumGenerator;
import io.camunda.zeebe.engine.processing.deployment.model.validation.DeploymentValidator;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.FeatureFlags;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public final class DeploymentTransformer {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;
  private final DeploymentValidator validator;
  private final List<DeploymentResourceTransformer> resourceTransformers;
  private final ChecksumGenerator checksumGenerator = new ChecksumGenerator();

  public DeploymentTransformer(
      final StateWriter stateWriter,
      final ProcessingState processingState,
      final ExpressionProcessor expressionProcessor,
      final KeyGenerator keyGenerator,
      final FeatureFlags featureFlags,
      final ValidationConfig config,
      final InstantSource clock,
      final ExpressionLanguageMetrics expressionLanguageMetrics,
      final ProcessDefinitionMetrics processDefinitionMetrics) {
    validator = new DeploymentValidator(config);

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
            expressionLanguageMetrics,
            processDefinitionMetrics,
            new ClusterVersionFeatures(processingState.getClusterVersionState()));

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

    final var defaultResourceTransformer =
        new DefaultResourceTransformer(
            keyGenerator, stateWriter, checksumGenerator, processingState.getResourceState());

    // Order matters: transformers are checked in order, and the first that can handle the resource
    // will be used. DefaultResourceTransformer should be last as it accepts any file.
    resourceTransformers =
        List.of(
            bpmnResourceTransformer,
            dmnResourceTransformer,
            formResourceTransformer,
            rpaTransformer,
            defaultResourceTransformer);
  }

  public DirectBuffer getChecksum(final byte[] resource) {
    return wrapArray(checksumGenerator.checksum(resource));
  }

  public Either<Failure, Void> transform(final DeploymentRecord deploymentEvent) {
    resourceTransformers.forEach(DeploymentResourceTransformer::reset);

    return validator
        .validateResources(deploymentEvent)
        .map(ok -> resolveTransformers(deploymentEvent))
        .flatMap(
            rwt ->
                buildMetadata(deploymentEvent, rwt)
                    .flatMap(contexts -> validator.validateMetadata(deploymentEvent, contexts))
                    .flatMap(ok -> writeResourceRecords(deploymentEvent, rwt)));
  }

  private List<ResourceWithTransformer> resolveTransformers(
      final DeploymentRecord deploymentEvent) {
    final List<ResourceWithTransformer> result = new ArrayList<>();
    for (final DeploymentResource deploymentResource : deploymentEvent.resources()) {
      final var transformer = getResourceTransformer(deploymentResource);
      result.add(new ResourceWithTransformer(deploymentResource, transformer));
    }
    return result;
  }

  /**
   * Iterates over all resources and builds metadata for each. Validates each resource individually
   * and adds its metadata to the deployment record.
   *
   * @param deploymentEvent the deployment record
   * @param resourcesWithTransformers resources paired with their resolved transformers
   * @return Either.right with the list of contexts produced by each transformer, or Either.left
   *     with error details
   */
  private Either<Failure, List<DeploymentResourceContext>> buildMetadata(
      final DeploymentRecord deploymentEvent,
      final List<ResourceWithTransformer> resourcesWithTransformers) {
    final var errors = new DeploymentErrorCollector();
    final List<DeploymentResourceContext> contexts = new ArrayList<>();

    for (final ResourceWithTransformer resourceWithTransformer : resourcesWithTransformers) {
      final var deploymentResource = resourceWithTransformer.resource;
      final var transformer = resourceWithTransformer.transformer;
      try {
        final var result = transformer.createMetadata(deploymentResource, deploymentEvent);

        if (result.isRight()) {
          contexts.add(result.get());
        } else {
          errors.add(result.getLeft().getMessage());
        }
      } catch (final RuntimeException e) {
        logAndCollectUnexpectedError(deploymentResource.getResourceName(), e, errors);
      }
    }

    return errors.toEither(contexts);
  }

  /**
   * Writes the actual resource records to state. This is called after all validation has passed.
   * Skips writing if the deployment contains only duplicates (versioning invariant).
   *
   * @param deploymentEvent the deployment record
   * @param resourcesWithTransformers resources paired with their resolved transformers
   */
  private Either<Failure, Void> writeResourceRecords(
      final DeploymentRecord deploymentEvent,
      final List<ResourceWithTransformer> resourcesWithTransformers) {
    if (deploymentEvent.hasDuplicatesOnly()) {
      return Either.right(null);
    }

    final var errors = new DeploymentErrorCollector();

    for (final ResourceWithTransformer resourceWithTransformer : resourcesWithTransformers) {
      final var deploymentResource = resourceWithTransformer.resource;
      final var transformer = resourceWithTransformer.transformer;
      try {
        transformer.writeRecords(deploymentResource, deploymentEvent);
      } catch (final RuntimeException e) {
        logAndCollectUnexpectedError(deploymentResource.getResourceName(), e, errors);
      }
    }

    return errors.toEither();
  }

  private DeploymentResourceTransformer getResourceTransformer(final DeploymentResource resource) {
    return resourceTransformers.stream()
        .filter(transformer -> transformer.canTransform(resource))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No transformer found for resource: " + resource.getResourceName()));
  }

  private static void logAndCollectUnexpectedError(
      final String resourceName,
      final RuntimeException exception,
      final DeploymentErrorCollector errors) {
    LOG.error("Unexpected error while processing resource '{}'", resourceName, exception);
    errors.add("'%s': %s", resourceName, exception.getMessage());
  }

  private record ResourceWithTransformer(
      DeploymentResource resource, DeploymentResourceTransformer transformer) {}
}
