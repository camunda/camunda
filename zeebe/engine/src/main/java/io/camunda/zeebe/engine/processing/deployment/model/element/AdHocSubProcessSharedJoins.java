/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * Finds the joining gateways of an ad-hoc sub-process that are reached from more than one ad-hoc
 * activity.
 *
 * <p>Each activated ad-hoc activity runs in its own inner instance. A joining gateway counts the
 * taken sequence flows per flow scope, so a join that merges the paths of different ad-hoc
 * activities can only fire if it lives in the ad-hoc sub-process instance instead of in one of the
 * inner instances.
 */
@NullMarked
final class AdHocSubProcessSharedJoins {

  private AdHocSubProcessSharedJoins() {}

  static Set<ExecutableFlowNode> compute(final Collection<ExecutableFlowNode> adHocActivities) {
    final Map<ExecutableFlowNode, ExecutableFlowNode> firstActivityByJoin = new IdentityHashMap<>();
    final Set<ExecutableFlowNode> sharedJoins = Collections.newSetFromMap(new IdentityHashMap<>());

    for (final ExecutableFlowNode adHocActivity : adHocActivities) {
      for (final ExecutableFlowNode join : findReachableJoiningGateways(adHocActivity)) {
        final var firstActivity = firstActivityByJoin.putIfAbsent(join, adHocActivity);
        if (firstActivity != null && firstActivity != adHocActivity) {
          sharedJoins.add(join);
        }
      }
    }
    return Collections.unmodifiableSet(sharedJoins);
  }

  private static Set<ExecutableFlowNode> findReachableJoiningGateways(
      final ExecutableFlowNode adHocActivity) {
    final Set<ExecutableFlowNode> joins = Collections.newSetFromMap(new IdentityHashMap<>());
    final Set<ExecutableFlowNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    final var pending = new ArrayDeque<ExecutableFlowNode>();
    pending.push(adHocActivity);

    while (!pending.isEmpty()) {
      final var node = pending.pop();
      if (!visited.add(node)) {
        continue;
      }
      if (isJoiningGateway(node)) {
        joins.add(node);
      }
      node.getOutgoing().forEach(flow -> pending.push(flow.getTarget()));
      if (node instanceof final ExecutableActivity activity) {
        activity.getBoundaryEvents().forEach(pending::push);
      }
    }
    return joins;
  }

  private static boolean isJoiningGateway(final ExecutableFlowNode node) {
    final var elementType = node.getElementType();
    return (elementType == BpmnElementType.PARALLEL_GATEWAY
            || elementType == BpmnElementType.INCLUSIVE_GATEWAY)
        && node.getIncoming().size() > 1;
  }
}
