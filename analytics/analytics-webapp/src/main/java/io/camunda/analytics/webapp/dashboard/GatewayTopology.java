/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The decision points of a deployed BPMN model: every <em>exclusive</em> gateway with two or more
 * outgoing sequence flows, each with its outgoing targets. Non-exclusive gateways (parallel,
 * event-based, inclusive) are deliberately not selected — a parallel gateway takes every branch, so
 * it is not a decision — and a single-outgoing exclusive gateway is a merge/pass-through, skipped
 * for the same reason. Parsed once per process definition (a definition key is immutable — a
 * redeploy mints a new key), so callers cache the result keyed by {@code processDefinitionKey}.
 */
final class GatewayTopology {

  private GatewayTopology() {}

  /** One decision gateway: its id, display label and outgoing branch targets, in model order. */
  record GatewaySpec(String gatewayId, String gatewayLabel, List<BranchSpec> branches) {}

  /** One outgoing branch: the flow's target element id and a display label. */
  record BranchSpec(String targetId, String targetLabel) {}

  /** Parses the model's decision gateways out of its XML; malformed XML yields no gateways. */
  static List<GatewaySpec> parse(final String bpmnXml) {
    final BpmnModelInstance model;
    try {
      model =
          Bpmn.readModelFromStream(
              new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
    } catch (final RuntimeException e) {
      // A definition the model API cannot read serves an empty card, never a 500.
      return List.of();
    }
    final List<GatewaySpec> gateways = new ArrayList<>();
    for (final ExclusiveGateway gateway : model.getModelElementsByType(ExclusiveGateway.class)) {
      final List<SequenceFlow> outgoing = List.copyOf(gateway.getOutgoing());
      if (outgoing.size() < 2) {
        continue; // a merge or pass-through, not a decision
      }
      final List<BranchSpec> branches = new ArrayList<>(outgoing.size());
      for (final SequenceFlow flow : outgoing) {
        final FlowNode target = flow.getTarget();
        branches.add(
            new BranchSpec(
                target.getId(), firstNonBlank(flow.getName(), target.getName(), target.getId())));
      }
      gateways.add(
          new GatewaySpec(
              gateway.getId(),
              firstNonBlank(gateway.getName(), gateway.getId()),
              List.copyOf(branches)));
    }
    return List.copyOf(gateways);
  }

  private static String firstNonBlank(final String... candidates) {
    for (final String candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        return candidate;
      }
    }
    return "";
  }
}
