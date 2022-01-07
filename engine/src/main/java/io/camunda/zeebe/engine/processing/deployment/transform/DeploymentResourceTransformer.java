/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.util.Either;

interface DeploymentResourceTransformer {

  /**
   * Transform the given resource. As a result, the transformer should add the deployed resource to
   * the deployment record and write an event for the resource (e.g. a process record).
   *
   * @param resource the resource to transform
   * @param deployment the deployment to add the deployed resource to
   * @return either {@link Either.Right} if the resource is transformed successfully, or {@link
   *     Either.Left} if the transformation failed
   */
  Either<Failure, Void> transformResource(DeploymentResource resource, DeploymentRecord deployment);
}
