/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import java.util.Set;

/**
 * Outcome of a failed {@link DeploymentTransformer#transform} call.
 *
 * <p>Carries the deployment categories whose transformers were dispatched during the (failed) run.
 * Callers use this to invalidate the matching state caches so that no in-memory entries from the
 * rolled-back deployment leak into subsequent processing.
 */
public record DeploymentTransformationFailure(
    String message, Set<DeploymentResourceCategory> touchedCategories) {}
