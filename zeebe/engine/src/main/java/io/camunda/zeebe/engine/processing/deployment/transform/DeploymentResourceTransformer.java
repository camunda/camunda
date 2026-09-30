/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.util.Either;

interface DeploymentResourceTransformer {

  /**
   * Determines if this transformer can handle the given resource.
   *
   * <p>Transformers are checked in order, and the first transformer that returns {@code true} will
   * be used to process the resource. This allows transformers to make decisions based on file
   * extension, content, or any other criteria.
   *
   * @param resource the resource to check
   * @return {@code true} if this transformer can handle the resource, {@code false} otherwise
   */
  boolean canTransform(DeploymentResource resource);

  /**
   * Step 1 of transforming the given resource: The transformer should add the deployed resource's
   * metadata to the deployment record, but not write any event records yet.
   *
   * <p>This method validates the internal consistency of a single resource file (e.g., valid XML,
   * valid JSON structure) and creates metadata entries. Cross-file validation (e.g., duplicate IDs
   * across the deployment) is handled by the DeploymentTransformer after all metadata is collected.
   *
   * <p>Transformers may return a {@link DeploymentResourceContext} carrying additional information
   * needed by later validation steps (e.g., BPMN deployment binding elements). Transformers that
   * have no additional context should return {@link DeploymentResourceContext#NONE}.
   *
   * @param resource the resource to transform
   * @param deployment the deployment to add the deployed resource to
   * @return either {@link Either.Right} with the resource context if the resource is transformed
   *     successfully, or {@link Either.Left} if the transformation failed
   */
  Either<Failure, DeploymentResourceContext> createMetadata(
      final DeploymentResource resource, final DeploymentRecord deployment);

  /**
   * Step 2 of transforming the given resource: The transformer should update the previously created
   * metadata (if necessary) and eventually write the actual event record(s) (e.g. "process
   * created").
   *
   * <p><b>Versioning invariant:</b> when the deployment contains at least one non-duplicate
   * resource, every resource that was individually marked as a duplicate in {@link #createMetadata}
   * must have its {@code duplicate} flag cleared and must receive a fresh key and a new version
   * number before the record is written. This ensures all resources in a deployment are versioned
   * together. Note that the caller (DeploymentTransformer) will skip calling this method entirely
   * for all-duplicate deployments by checking {@link DeploymentRecord#hasDuplicatesOnly()} — see
   * {@link
   * io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord#hasDuplicatesOnly()}.
   *
   * @param resource the resource to transform
   * @param deployment the deployment record containing the metadata created in {@link
   *     DeploymentResourceTransformer#createMetadata(DeploymentResource, DeploymentRecord)}
   */
  void writeRecords(DeploymentResource resource, DeploymentRecord deployment);
}
