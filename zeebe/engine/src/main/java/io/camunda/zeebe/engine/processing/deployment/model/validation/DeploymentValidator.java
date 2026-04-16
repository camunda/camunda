/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.transform.BpmnElementsWithDeploymentBinding;
import io.camunda.zeebe.engine.processing.deployment.transform.DeploymentErrorCollector;
import io.camunda.zeebe.engine.processing.deployment.transform.DeploymentResourceContext;
import io.camunda.zeebe.engine.processing.deployment.transform.ValidationConfig;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.util.Either;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Validates deployment records at various stages of the deployment pipeline.
 *
 * <p>This class is stateless and can be reused across deployments. It covers:
 *
 * <ul>
 *   <li>Structural validation (non-empty deployment, resource name length)
 *   <li>Cross-resource ID uniqueness (processes, decisions, forms, generic resources)
 *   <li>BPMN deployment binding satisfaction
 * </ul>
 */
public final class DeploymentValidator {

  private final ValidationConfig config;

  public DeploymentValidator(final ValidationConfig config) {
    this.config = config;
  }

  /**
   * Validates structural properties of all resources in the deployment.
   *
   * <p>Checks that the deployment is non-empty and that all resource names are within the
   * configured maximum length.
   *
   * @param deployment the deployment to validate
   * @return Either.right(null) if valid, or Either.left with all validation errors
   */
  public Either<Failure, Void> validateResources(final DeploymentRecord deployment) {
    if (!deployment.resources().iterator().hasNext()) {
      return Either.left(new Failure("Expected to deploy at least one resource, but none given"));
    }

    final var errors = new DeploymentErrorCollector();

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
   * Validates cross-resource constraints on the collected metadata.
   *
   * <p>First checks for duplicate resource IDs across the deployment, then validates that all BPMN
   * deployment bindings are satisfied.
   *
   * @param deployment the deployment record
   * @param contexts the contexts produced by each transformer during metadata creation
   * @return Either.right(null) if validation succeeds, or Either.left with validation errors
   */
  public Either<Failure, Void> validateMetadata(
      final DeploymentRecord deployment, final List<DeploymentResourceContext> contexts) {
    return validateResourceIds(deployment)
        .flatMap(ok -> validateDeploymentBindings(deployment, contexts));
  }

  /**
   * Validates that there are no conflicting resource IDs within the deployment.
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
   */
  private Either<Failure, Void> validateResourceIds(final DeploymentRecord deployment) {
    final var errors = new DeploymentErrorCollector();

    checkForDuplicateIds(
        deployment.getProcessesMetadata(),
        metadata -> metadata.getBpmnProcessId(),
        metadata -> metadata.getResourceName(),
        "Duplicated process id in resources '%2$s' and '%3$s'",
        errors);

    checkForDuplicateIds(
        deployment.getDecisionRequirementsMetadata(),
        metadata -> metadata.getDecisionRequirementsId(),
        metadata -> metadata.getResourceName(),
        "Expected the decision requirements ids to be unique within a deployment"
            + " but found a duplicated id '%s' in the resources '%s' and '%s'",
        errors);

    checkForDuplicateIds(
        deployment.getDecisionsMetadata(),
        metadata -> metadata.getDecisionId(),
        metadata -> deployment.getResourceNameForDecision(metadata),
        "Expected the decision ids to be unique within a deployment"
            + " but found a duplicated id '%s' in the resources '%s' and '%s'",
        errors);

    checkForDuplicateIds(
        deployment.getFormMetadata(),
        metadata -> metadata.getFormId(),
        metadata -> metadata.getResourceName(),
        "Expected the form ids to be unique within a deployment"
            + " but found a duplicated id '%s' in the resources '%s' and '%s'.",
        errors);

    checkForDuplicateIds(
        deployment.getResourceMetadata(),
        metadata -> metadata.getResourceId(),
        metadata -> metadata.getResourceName(),
        "Expected the resource ids to be unique within a deployment"
            + " but found a duplicated id '%s' in the resources '%s' and '%s'.",
        errors);

    return errors.toEither();
  }

  /**
   * Checks a collection of metadata entries for duplicate IDs and appends errors for each duplicate
   * found.
   *
   * @param items the metadata entries to check
   * @param idExtractor extracts the ID from a metadata entry
   * @param nameExtractor extracts the resource name from a metadata entry (for error messages)
   * @param messageFormat format string with 3 placeholders: id, first resource name, second
   *     resource name
   * @param errors the error collector to append duplicate errors to
   */
  private <T> void checkForDuplicateIds(
      final Iterable<T> items,
      final Function<T, String> idExtractor,
      final Function<T, String> nameExtractor,
      final String messageFormat,
      final DeploymentErrorCollector errors) {
    final var firstOccurrence = new HashMap<String, String>();

    for (final T item : items) {
      final var id = idExtractor.apply(item);
      final var resourceName = Objects.requireNonNullElse(nameExtractor.apply(item), "<?>");
      final var previousResourceName = firstOccurrence.putIfAbsent(id, resourceName);
      if (previousResourceName != null) {
        errors.add(messageFormat, id, previousResourceName, resourceName);
      }
    }
  }

  /**
   * Validates deployment bindings for BPMN resources in the deployment. Ensures that all referenced
   * resources (via zeebe:calledElement, zeebe:calledDecision, zeebe:formDefinition,
   * zeebe:linkedResource) with binding type "deployment" are present in the deployment.
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

    final var errors = new DeploymentErrorCollector();

    final var validator = new BpmnDeploymentBindingValidator(deploymentEvent);
    for (final var elements : bpmnContexts) {
      final var validationError = validator.validate(elements);
      if (validationError != null) {
        errors.add("'%s':\n%s", elements.getResourceName(), validationError);
      }
    }

    return errors.toEither();
  }
}
