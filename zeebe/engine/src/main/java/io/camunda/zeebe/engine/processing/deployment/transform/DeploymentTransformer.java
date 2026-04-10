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
import io.camunda.zeebe.engine.processing.deployment.model.validation.BpmnDeploymentBindingValidator;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.FeatureFlags;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public final class DeploymentTransformer {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;
  private final ValidationConfig config;
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
    this.config = config;

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
    return validateResources(deploymentEvent)
        // Step 2: Parse each resource and build metadata
        .flatMap(ok -> buildMetadata(deploymentEvent))
        // Step 3+4: Validate cross-resource constraints (duplicate IDs, deployment bindings)
        .flatMap(contexts -> validateMetadata(deploymentEvent, contexts))
        // Step 5: Write the actual resources/deployment to state
        .map(
            ok -> {
              writeResourceRecords(deploymentEvent);
              return null;
            });
  }

  /**
   * Step 1: Validates structural properties of all resources in the deployment.
   *
   * <p>Checks that the deployment is non-empty and that all resource names are within the
   * configured maximum length.
   *
   * @param deployment the deployment to validate
   * @return Either.right(null) if valid, or Either.left with all validation errors
   */
  private Either<Failure, Void> validateResources(final DeploymentRecord deployment) {
    if (!deployment.resources().iterator().hasNext()) {
      return Either.left(new Failure("Expected to deploy at least one resource, but none given"));
    }

    final var errors = new ErrorCollector();

    for (final DeploymentResource resource : deployment.resources()) {
      final var resourceName = resource.getResourceName();
      if (resourceName.length() > config.maxNameFieldLength()) {
        errors.add(
            "- Resource name '%s' exceeds maximum length of %d characters"
                + " as it has a length of %d characters",
            resourceName, config.maxNameFieldLength(), resourceName.length());
      }
    }

    return errors.toEither();
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
   * Step 3+4: Validates cross-resource constraints on the collected metadata.
   *
   * <p>First checks for duplicate resource IDs across the deployment, then validates that all BPMN
   * deployment bindings are satisfied.
   *
   * @param deployment the deployment record
   * @param contexts the contexts produced by each transformer during metadata creation
   * @return Either.right(null) if validation succeeds, or Either.left with validation errors
   */
  private Either<Failure, Void> validateMetadata(
      final DeploymentRecord deployment, final List<DeploymentResourceContext> contexts) {
    return validateResourceIds(deployment)
        .flatMap(ok -> validateDeploymentBindings(deployment, contexts));
  }

  /**
   * Step 3: Validates that there are no conflicting resource IDs within the deployment.
   *
   * <p>Checks all metadata collections for duplicate IDs:
   *
   * <ul>
   *   <li>BPMN process IDs (bpmnProcessId)
   *   <li>DMN decision requirements IDs (decisionRequirementsId)
   *   <li>DMN decision IDs (decisionId)
   *   <li>Form IDs (formId)
   *   <li>Generic resource IDs (resourceId)
   * </ul>
   *
   * @param deployment the deployment record containing all metadata
   * @return Either.right(null) if no conflicts found, or Either.left with error details
   */
  private Either<Failure, Void> validateResourceIds(final DeploymentRecord deployment) {
    final var errors = new ErrorCollector();

    checkForDuplicateIds(
        deployment.getProcessesMetadata(),
        metadata -> metadata.getBpmnProcessId(),
        metadata -> metadata.getResourceName(),
        "process id",
        errors);

    checkForDuplicateIds(
        deployment.getDecisionRequirementsMetadata(),
        metadata -> metadata.getDecisionRequirementsId(),
        metadata -> metadata.getResourceName(),
        "decision requirements id",
        errors);

    checkForDuplicateIds(
        deployment.getDecisionsMetadata(),
        metadata -> metadata.getDecisionId(),
        metadata -> {
          final var name = deployment.getResourceNameForDecision(metadata);
          return name != null ? name : "<?>";
        },
        "decision id",
        errors);

    checkForDuplicateIds(
        deployment.getFormMetadata(),
        metadata -> metadata.getFormId(),
        metadata -> metadata.getResourceName(),
        "form id",
        errors);

    checkForDuplicateIds(
        deployment.getResourceMetadata(),
        metadata -> metadata.getResourceId(),
        metadata -> metadata.getResourceName(),
        "resource id",
        errors);

    return errors.toEither(
        "Expected resource IDs to be unique within a deployment,"
            + " but encountered the following conflicting IDs:");
  }

  /**
   * Checks a collection of metadata entries for duplicate IDs and appends errors for each duplicate
   * found.
   *
   * @param items the metadata entries to check
   * @param idExtractor extracts the ID from a metadata entry
   * @param nameExtractor extracts the resource name from a metadata entry (for error messages)
   * @param typeLabel the human-readable type label (e.g. "process id", "form id")
   * @param errors the error collector to append duplicate errors to
   */
  private <T> void checkForDuplicateIds(
      final Iterable<T> items,
      final Function<T, String> idExtractor,
      final Function<T, String> nameExtractor,
      final String typeLabel,
      final ErrorCollector errors) {
    final var seen = new HashSet<String>();
    final var duplicates = new HashMap<String, List<String>>();

    for (final T item : items) {
      final var id = idExtractor.apply(item);
      if (!seen.add(id)) {
        duplicates.computeIfAbsent(id, k -> new ArrayList<>()).add(nameExtractor.apply(item));
      }
    }

    for (final var entry : duplicates.entrySet()) {
      errors.add(
          "- Duplicated %s '%s' in resources: %s",
          typeLabel, entry.getKey(), String.join(", ", entry.getValue()));
    }
  }

  /**
   * Step 4: Validates deployment bindings for BPMN resources in the deployment. Ensures that all
   * referenced resources (via zeebe:calledElement, zeebe:calledDecision, zeebe:formDefinition,
   * zeebe:linkedResource) with binding type "deployment" are present in the deployment.
   *
   * @param deploymentEvent the deployment record
   * @param contexts the contexts produced by each transformer during metadata creation
   * @return Either.right(null) if validation succeeds, or Either.left with validation errors
   */
  private Either<Failure, Void> validateDeploymentBindings(
      final DeploymentRecord deploymentEvent, final List<DeploymentResourceContext> contexts) {
    final var bpmnContexts =
        contexts.stream()
            .filter(BpmnElementsWithDeploymentBinding.class::isInstance)
            .map(BpmnElementsWithDeploymentBinding.class::cast)
            .toList();

    if (bpmnContexts.isEmpty()) {
      return Either.right(null);
    }

    final var errors = new ErrorCollector();

    final var validator = new BpmnDeploymentBindingValidator(deploymentEvent);
    for (final var elements : bpmnContexts) {
      final var validationError = validator.validate(elements);
      if (validationError != null) {
        errors.add(validationError);
      }
    }

    return errors.toEither();
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

  /** Collects validation errors and converts them into a {@link Failure}. */
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

    String formatMessage(final String prefix) {
      return prefix + errors;
    }

    String formatMessage() {
      return formatMessage(DEFAULT_PREFIX);
    }

    Either<Failure, Void> toEither() {
      return toEither(DEFAULT_PREFIX);
    }

    Either<Failure, Void> toEither(final String prefix) {
      if (hasErrors()) {
        return Either.left(new Failure(formatMessage(prefix)));
      }
      return Either.right(null);
    }

    <T> Either<Failure, T> toEither(final T value) {
      if (hasErrors()) {
        return Either.left(new Failure(formatMessage()));
      }
      return Either.right(value);
    }
  }
}
