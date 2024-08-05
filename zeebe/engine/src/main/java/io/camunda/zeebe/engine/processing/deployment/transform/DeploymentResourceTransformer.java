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
   * Step 1 of transforming the given resource: The transformer should add the deployed resource's
   * metadata to the deployment record, but not write any event records yet.
   *
   * @param resource the resource to transform
   * @param deployment the deployment to add the deployed resource to
   * @return either {@link Either.Right} if the resource is transformed successfully, or {@link
   *     Either.Left} if the transformation failed
   */
  Either<Failure, Void> createMetadata(
      final DeploymentResource resource, final DeploymentRecord deployment);

  /**
   * Step 2 of transforming the given resource: The transformer should update the previously created
   * metadata (if necessary) and eventually write the actual event record(s) (e.g. "process
   * created").
   *
   * @param resource the resource to transform
   * @param deployment the deployment record containing the metadata created in {@link
   *     DeploymentResourceTransformer#createMetadata(DeploymentResource, DeploymentRecord)}
   * @return either {@link Either.Right} if the resource is transformed successfully, or {@link
   *     Either.Left} if the transformation failed
   */
  Either<Failure, Void> writeRecords(DeploymentResource resource, DeploymentRecord deployment);
}
