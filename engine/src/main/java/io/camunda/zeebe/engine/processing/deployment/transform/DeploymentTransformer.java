/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapArray;
import static java.util.Map.entry;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.util.Either;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public final class DeploymentTransformer {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;

  private static final DeploymentResourceTransformer UNKNOWN_RESOURCE =
      new UnknownResourceTransformer();

  private final Map<String, DeploymentResourceTransformer> resourceTransformers;

  private final MessageDigest digestGenerator;
  // internal changes during processing
  private RejectionType rejectionType;
  private String rejectionReason;

  public DeploymentTransformer(
      final StateWriter stateWriter,
      final ZeebeState zeebeState,
      final ExpressionProcessor expressionProcessor,
      final KeyGenerator keyGenerator) {

    try {
      // We get an alert by LGTM, since MD5 is a weak cryptographic hash function,
      // but it is not easy to exchange this weak algorithm without getting compatibility issues
      // with previous versions. Furthermore it is very unlikely that we get problems on checking
      // the deployments hashes.
      digestGenerator =
          MessageDigest.getInstance("MD5"); // lgtm [java/weak-cryptographic-algorithm]
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }

    final var bpmnResourceTransformer =
        new BpmnResourceTransformer(
            keyGenerator,
            stateWriter,
            this::getChecksum,
            zeebeState.getProcessState(),
            expressionProcessor);
    final var dmnResourceTransformer =
        new DmnResourceTransformer(
            keyGenerator, stateWriter, this::getChecksum, zeebeState.getDecisionState());

    resourceTransformers =
        Map.ofEntries(
            entry(".bpmn", bpmnResourceTransformer),
            entry(".xml", bpmnResourceTransformer),
            entry(".dmn", dmnResourceTransformer));
  }

  private DirectBuffer getChecksum(final DeploymentResource resource) {
    return wrapArray(digestGenerator.digest(resource.getResource()));
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

    while (resourceIterator.hasNext()) {
      final DeploymentResource deploymentResource = resourceIterator.next();
      success &= transformResource(deploymentEvent, errors, deploymentResource);
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

  private boolean transformResource(
      final DeploymentRecord deploymentEvent,
      final StringBuilder errors,
      final DeploymentResource deploymentResource) {
    final String resourceName = deploymentResource.getResourceName();

    final var transformer = getResourceTransformer(resourceName);

    try {
      final var result = transformer.transformResource(deploymentResource, deploymentEvent);

      if (result.isRight()) {
        return true;
      } else {
        final var failureMessage = result.getLeft().getMessage();
        errors.append("\n").append(failureMessage);
        return false;
      }

    } catch (final RuntimeException e) {
      LOG.error("Unexpected error while processing resource '{}'", resourceName, e);
      errors.append("\n'").append(resourceName).append("': ").append(e.getMessage());
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
        .orElse(UNKNOWN_RESOURCE);
  }

  private static class UnknownResourceTransformer implements DeploymentResourceTransformer {

    @Override
    public Either<Failure, Void> transformResource(
        final DeploymentResource resource, final DeploymentRecord deployment) {

      final var failureMessage =
          String.format("%n'%s': unknown resource type", resource.getResourceName());
      return Either.left(new Failure(failureMessage));
    }
  }
}
