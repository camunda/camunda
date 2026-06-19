/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.engine.processing.clusterversion.ClusterVersionFeatures;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * Deployment-admission gate for the cancel-execution-listener feature (PR #46880). When the cluster
 * has not activated {@link Capability#CANCEL_EXECUTION_LISTENER}, deployments containing cancel
 * listeners on a {@code <process>} element are rejected with a clear, operator-actionable message.
 * The pure runtime gate in {@code ProcessProcessor} would silently ignore the listeners —
 * preventing safety issues but leaving users wondering why their listeners never fire. Rejecting at
 * deployment time makes the feature flag fail loud: either the cluster is at the right ECV and the
 * deployment succeeds, or the operator gets an explicit "raise first" instruction.
 */
public final class ClusterVersionCancelListenerValidator {

  private ClusterVersionCancelListenerValidator() {}

  public static Either<Failure, ?> validate(
      final DeploymentResource resource,
      final List<ExecutableProcess> executableProcesses,
      final ClusterVersionFeatures features) {

    if (features.isActive(Capability.CANCEL_EXECUTION_LISTENER)) {
      return Either.right(null);
    }

    // Check the top-level process only. The upstream BPMN syntactic validator
    // (ExecutionListenersValidator in bpmn-model) restricts cancel listeners to <process>
    // elements and runs before this validator, so a deployment with cancel listeners on any
    // other element type has already been rejected. If that syntactic rule is ever relaxed,
    // this check must be broadened correspondingly.
    final List<String> rejectedProcessIds = new ArrayList<>();
    for (final ExecutableProcess process : executableProcesses) {
      if (process.hasCancelExecutionListeners()) {
        rejectedProcessIds.add(BufferUtil.bufferAsString(process.getId()));
      }
    }

    if (rejectedProcessIds.isEmpty()) {
      return Either.right(null);
    }

    final String message =
        """
        Resource '%s' declares cancel execution listeners on process(es) %s, but the cluster has \
        not activated the '%s' capability. Raise the cluster's ECV before deploying — for \
        example: POST /v2/cluster-version/raise {"capability":"%s"}."""
            .formatted(
                resource.getResourceName(),
                rejectedProcessIds,
                Capability.CANCEL_EXECUTION_LISTENER.name(),
                Capability.CANCEL_EXECUTION_LISTENER.name());
    return Either.left(new Failure(message));
  }
}
