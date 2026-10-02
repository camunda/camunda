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
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * Groups the ad-hoc activities of an ad-hoc sub-process by the joining gateways they lead into.
 *
 * <p>Each activated ad-hoc activity is placed in an inner instance, which is its flow scope. A
 * joining gateway counts the taken sequence flows per flow scope. If two ad-hoc activities lead
 * into the same joining gateway, they must share an inner instance, or the gateway can never count
 * all of its incoming sequence flows.
 */
@NullMarked
final class AdHocSubProcessJoinGroups {

  private AdHocSubProcessJoinGroups() {}

  /**
   * @return the join group id by ad-hoc activity id; ad-hoc activities that don't lead into a
   *     joining gateway are absent
   */
  static Map<String, String> compute(final Map<String, ExecutableFlowNode> adHocActivitiesById) {
    final Map<String, String> parentById = new HashMap<>();
    final Map<ExecutableFlowNode, String> firstActivityIdByJoin = new IdentityHashMap<>();

    for (final String activityId : adHocActivitiesById.keySet()) {
      for (final ExecutableFlowNode join :
          findReachableJoiningGateways(adHocActivitiesById.get(activityId))) {
        parentById.putIfAbsent(activityId, activityId);
        final String otherActivityId = firstActivityIdByJoin.putIfAbsent(join, activityId);
        if (otherActivityId != null) {
          parentById.put(findRoot(parentById, activityId), findRoot(parentById, otherActivityId));
        }
      }
    }

    final Map<String, String> joinGroupIdByActivityId = new HashMap<>();
    parentById.keySet().forEach(id -> joinGroupIdByActivityId.put(id, findRoot(parentById, id)));
    return Collections.unmodifiableMap(joinGroupIdByActivityId);
  }

  private static String findRoot(final Map<String, String> parentById, final String id) {
    String root = id;
    String parent = parentById.get(root);
    while (parent != null && !parent.equals(root)) {
      root = parent;
      parent = parentById.get(root);
    }
    return root;
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
