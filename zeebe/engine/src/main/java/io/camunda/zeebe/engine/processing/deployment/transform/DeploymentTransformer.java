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
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.FeatureFlags;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public final class DeploymentTransformer {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;
  private final ValidationConfig config;
  private final List<DeploymentResourceTransformer> resourceTransformers;
  private final ChecksumGenerator checksumGenerator = new ChecksumGenerator();
  // internal changes during processing
  private RejectionType rejectionType;
  private String rejectionReason;

  private final BpmnResourceTransformer bpmnResourceTransformer;

  // Track BPMN resources for deployment binding validation (step 4)
  private final List<BpmnResource> bpmnResources = new ArrayList<>();

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

    bpmnResourceTransformer =
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
    // Step 1: Check if any resources, if not reject
    return checkHasResources(deploymentEvent)
        // Step 2: Iterate over all resources and build metadata
        .flatMap(ok -> buildMetadataForAllResources(deploymentEvent))
        // Step 3: Check for conflicting resource IDs within the single deployment
        .flatMap(ok -> checkForConflictingIds(deploymentEvent))
        // Step 4: Check if all deployment bindings are satisfied
        .flatMap(ok -> validateAllDeploymentBindings(deploymentEvent))
        // Step 5: Write the actual resources/deployment to state
        .map(
            ok -> {
              writeResourceRecords(deploymentEvent);
              return null;
            });
  }

  /**
   * Step 1: Checks that the deployment contains at least one resource.
   *
   * @param deployment the deployment to check
   * @return Either.right(null) if resources exist, or Either.left with a rejection reason
   */
  private Either<Failure, Void> checkHasResources(final DeploymentRecord deployment) {
    final Iterator<DeploymentResource> resourceIterator = deployment.resources().iterator();
    if (!resourceIterator.hasNext()) {
      rejectionType = RejectionType.INVALID_ARGUMENT;
      rejectionReason = "Expected to deploy at least one resource, but none given";
      return Either.left(new Failure(rejectionReason));
    }
    return Either.right(null);
  }

  /**
   * Step 2: Iterates over all resources and builds metadata for each. This step validates each
   * resource and adds its metadata to the deployment record.
   *
   * @param deploymentEvent the deployment record
   * @return Either.right(null) if successful, or Either.left with error details
   */
  private Either<Failure, Void> buildMetadataForAllResources(
      final DeploymentRecord deploymentEvent) {
    final StringBuilder errors = new StringBuilder();
    boolean success = true;

    // Clear BPMN resources from previous transform
    bpmnResources.clear();

    final Iterator<DeploymentResource> resourceIterator = deploymentEvent.resources().iterator();
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

    if (!success) {
      rejectionType = RejectionType.INVALID_ARGUMENT;
      rejectionReason =
          String.format(
              "Expected to deploy new resources, but encountered the following errors:%s", errors);
      return Either.left(new Failure(rejectionReason));
    }

    return Either.right(null);
  }

  /**
   * Step 3: Validates that there are no conflicting resource IDs within the deployment.
   *
   * <p>Checks all metadata collections for conflicting IDs:
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
  private Either<Failure, Void> checkForConflictingIds(final DeploymentRecord deployment) {
    final StringBuilder errors = new StringBuilder();
    boolean success = true;

    // Check for duplicate BPMN process IDs
    final var bpmnProcessIds = new java.util.HashSet<String>();
    final var bpmnDuplicates = new java.util.HashMap<String, java.util.List<String>>();
    for (final var processMetadata : deployment.getProcessesMetadata()) {
      final var processId = processMetadata.getBpmnProcessId();
      if (!bpmnProcessIds.add(processId)) {
        bpmnDuplicates
            .computeIfAbsent(processId, k -> new java.util.ArrayList<>())
            .add(processMetadata.getResourceName());
      }
    }
    for (final var entry : bpmnDuplicates.entrySet()) {
      success = false;
      errors
          .append("\n- Duplicated process id '")
          .append(entry.getKey())
          .append("' in resources: ")
          .append(String.join(", ", entry.getValue()));
    }

    // Check for duplicate DMN decision requirements IDs
    final var drgIds = new java.util.HashSet<String>();
    final var drgDuplicates = new java.util.HashMap<String, java.util.List<String>>();
    for (final var drgMetadata : deployment.getDecisionRequirementsMetadata()) {
      final var drgId = drgMetadata.getDecisionRequirementsId();
      if (!drgIds.add(drgId)) {
        drgDuplicates
            .computeIfAbsent(drgId, k -> new java.util.ArrayList<>())
            .add(drgMetadata.getResourceName());
      }
    }
    for (final var entry : drgDuplicates.entrySet()) {
      success = false;
      errors
          .append("\n- Duplicated decision requirements id '")
          .append(entry.getKey())
          .append("' in resources: ")
          .append(String.join(", ", entry.getValue()));
    }

    // Check for duplicate DMN decision IDs
    final var decisionIds = new java.util.HashSet<String>();
    final var decisionDuplicates = new java.util.HashMap<String, java.util.List<String>>();
    for (final var decisionMetadata : deployment.getDecisionsMetadata()) {
      final var decisionId = decisionMetadata.getDecisionId();
      if (!decisionIds.add(decisionId)) {
        // Find the resource name by looking up the parent DRG
        final var resourceName =
            deployment.getDecisionRequirementsMetadata().stream()
                .filter(
                    drg ->
                        drg.getDecisionRequirementsKey()
                            == decisionMetadata.getDecisionRequirementsKey())
                .map(drg -> drg.getResourceName())
                .findFirst()
                .orElse("<?>");
        decisionDuplicates
            .computeIfAbsent(decisionId, k -> new java.util.ArrayList<>())
            .add(resourceName);
      }
    }
    for (final var entry : decisionDuplicates.entrySet()) {
      success = false;
      errors
          .append("\n- Duplicated decision id '")
          .append(entry.getKey())
          .append("' in resources: ")
          .append(String.join(", ", entry.getValue()));
    }

    // Check for duplicate form IDs
    final var formIds = new java.util.HashSet<String>();
    final var formDuplicates = new java.util.HashMap<String, java.util.List<String>>();
    for (final var formMetadata : deployment.getFormMetadata()) {
      final var formId = formMetadata.getFormId();
      if (!formIds.add(formId)) {
        formDuplicates
            .computeIfAbsent(formId, k -> new java.util.ArrayList<>())
            .add(formMetadata.getResourceName());
      }
    }
    for (final var entry : formDuplicates.entrySet()) {
      success = false;
      errors
          .append("\n- Duplicated form id '")
          .append(entry.getKey())
          .append("' in resources: ")
          .append(String.join(", ", entry.getValue()));
    }

    // Check for duplicate generic resource IDs
    final var resourceIds = new java.util.HashSet<String>();
    final var resourceDuplicates = new java.util.HashMap<String, java.util.List<String>>();
    for (final var resourceMetadata : deployment.getResourceMetadata()) {
      final var resourceId = resourceMetadata.getResourceId();
      if (!resourceIds.add(resourceId)) {
        resourceDuplicates
            .computeIfAbsent(resourceId, k -> new java.util.ArrayList<>())
            .add(resourceMetadata.getResourceName());
      }
    }
    for (final var entry : resourceDuplicates.entrySet()) {
      success = false;
      errors
          .append("\n- Duplicated resource id '")
          .append(entry.getKey())
          .append("' in resources: ")
          .append(String.join(", ", entry.getValue()));
    }

    if (!success) {
      rejectionType = RejectionType.INVALID_ARGUMENT;
      rejectionReason =
          String.format(
              "Expected resource IDs to be unique within a deployment, but encountered the following conflicting IDs:%s",
              errors);
      return Either.left(new Failure(rejectionReason));
    }

    return Either.right(null);
  }

  /**
   * Step 4: Validates deployment bindings for all BPMN resources in the deployment. Ensures that
   * all referenced resources (via zeebe:calledElement, zeebe:calledDecision, zeebe:formDefinition,
   * zeebe:linkedResource) are present in the deployment.
   *
   * @param deploymentEvent the deployment record
   * @return Either.right(null) if validation succeeds, or Either.left with validation errors
   */
  private Either<Failure, Void> validateAllDeploymentBindings(
      final DeploymentRecord deploymentEvent) {
    if (bpmnResources.isEmpty()) {
      return Either.right(null);
    }

    final StringBuilder errors = new StringBuilder();
    boolean success = true;

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

    if (!success) {
      rejectionType = RejectionType.INVALID_ARGUMENT;
      rejectionReason =
          String.format(
              "Expected to deploy new resources, but encountered the following errors:%s", errors);
      return Either.left(new Failure(rejectionReason));
    }

    return Either.right(null);
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

    final StringBuilder errors = new StringBuilder();
    boolean success = true;

    for (final DeploymentResource deploymentResource : deploymentEvent.resources()) {
      success &= writeRecords(deploymentResource, deploymentEvent, errors);
    }

    if (!success) {
      rejectionType = RejectionType.INVALID_ARGUMENT;
      rejectionReason =
          String.format(
              "Expected to deploy new resources, but encountered the following errors:%s", errors);
      // Note: In practice, this should never happen as validation already passed
      throw new IllegalStateException(rejectionReason);
    }
  }

  private boolean isBpmnResource(final DeploymentResource resource) {
    return bpmnResourceTransformer.canTransform(resource);
  }

  private boolean createMetadata(
      final DeploymentResource deploymentResource,
      final DeploymentRecord deploymentEvent,
      final DeploymentResourceContext context,
      final StringBuilder errors) {
    final var transformer = getResourceTransformer(deploymentResource);
    final var resourceName = deploymentResource.getResourceName();

    if (resourceName.length() > config.maxNameFieldLength()) {
      errors.append(
          String.format(
              "\n- Resource name '%s' exceeds maximum length of %d characters as it has a length of %d characters",
              resourceName, config.maxNameFieldLength(), resourceName.length()));
      return false;
    }

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
      handleUnexpectedError(deploymentResource.getResourceName(), e, errors);
    }
    return false;
  }

  private boolean writeRecords(
      final DeploymentResource deploymentResource,
      final DeploymentRecord deploymentEvent,
      final StringBuilder errors) {
    final var transformer = getResourceTransformer(deploymentResource);
    try {
      transformer.writeRecords(deploymentResource, deploymentEvent);
      return true;
    } catch (final RuntimeException e) {
      handleUnexpectedError(deploymentResource.getResourceName(), e, errors);
    }
    return false;
  }

  public RejectionType getRejectionType() {
    return rejectionType;
  }

  public String getRejectionReason() {
    return rejectionReason;
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

  private static void handleUnexpectedError(
      final String resourceName, final RuntimeException exception, final StringBuilder errors) {
    LOG.error("Unexpected error while processing resource '{}'", resourceName, exception);
    errors.append("\n'").append(resourceName).append("': ").append(exception.getMessage());
  }

  private record BpmnResource(
      DeploymentResource resource, BpmnElementsWithDeploymentBinding elements) {}
}
