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
      final ExpressionLanguageMetrics expressionLanguageMetrics) {
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
    // Step 1: Validate structural properties of all resources (non-empty, name length)
    return validator
        .validateResources(deploymentEvent)
        // Step 2: Parse each resource and build metadata
        .flatMap(ok -> buildMetadata(deploymentEvent))
        // Step 3+4: Validate cross-resource constraints (duplicate IDs, deployment bindings)
        .flatMap(contexts -> validator.validateMetadata(deploymentEvent, contexts))
        // Step 5: Write the actual resources/deployment to state
        .map(
            ok -> {
              writeResourceRecords(deploymentEvent);
              return null;
            });
  }

  /**
   * Step 2: Iterates over all resources and builds metadata for each. This step validates each
   * resource individually and adds its metadata to the deployment record.
   *
   * @param deploymentEvent the deployment record
   * @return Either.right with the list of contexts produced by each transformer, or Either.left
   *     with error details
   */
  private Either<Failure, List<DeploymentResourceContext>> buildMetadata(
      final DeploymentRecord deploymentEvent) {
    final var errors = new ErrorCollector();
    final List<DeploymentResourceContext> contexts = new ArrayList<>();

    for (final DeploymentResource deploymentResource : deploymentEvent.resources()) {
      final var transformer = getResourceTransformer(deploymentResource);
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
   * Step 5: Writes the actual resource records to state. This is called after all validation has
   * passed. Skips writing if the deployment contains only duplicates (versioning invariant).
   *
   * @param deploymentEvent the deployment record with metadata
   */
  private void writeResourceRecords(final DeploymentRecord deploymentEvent) {
    // Check if all resources are duplicates - if so, skip writing entirely (versioning invariant)
    if (deploymentEvent.hasDuplicatesOnly()) {
      return;
    }

    final var errors = new ErrorCollector();

    for (final DeploymentResource deploymentResource : deploymentEvent.resources()) {
      final var transformer = getResourceTransformer(deploymentResource);
      try {
        transformer.writeRecords(deploymentResource, deploymentEvent);
      } catch (final RuntimeException e) {
        logAndCollectUnexpectedError(deploymentResource.getResourceName(), e, errors);
      }
    }

    if (errors.hasErrors()) {
      // Note: In practice, this should never happen as validation already passed
      throw new IllegalStateException(errors.formatMessage());
    }
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
      final String resourceName, final RuntimeException exception, final ErrorCollector errors) {
    LOG.error("Unexpected error while processing resource '{}'", resourceName, exception);
    errors.add("'%s': %s", resourceName, exception.getMessage());
  }

  /** Collects errors during metadata building and record writing. */
  private static final class ErrorCollector {

    private static final String DEFAULT_PREFIX =
        "Expected to deploy new resources, but encountered the following errors:";

    private final StringBuilder errors = new StringBuilder();

    void add(final String message) {
      errors.append("\n").append(message);
    }

    void add(final String format, final Object... args) {
      errors.append("\n").append(String.format(format, args));
    }

    boolean hasErrors() {
      return !errors.isEmpty();
    }

    String formatMessage() {
      return DEFAULT_PREFIX + errors;
    }

    <T> Either<Failure, T> toEither(final T value) {
      if (hasErrors()) {
        return Either.left(new Failure(formatMessage()));
      }
      return Either.right(value);
    }
  }
}
