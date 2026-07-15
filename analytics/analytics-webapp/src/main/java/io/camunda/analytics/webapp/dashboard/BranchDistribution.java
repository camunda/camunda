/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

import java.util.List;

/**
 * How one exclusive gateway's traffic split over its outgoing branches in the selected range: the
 * gateway's own activation count and, per outgoing target, the target's activations plus its share
 * of the gateway's ({@code activations(target) / activations(gateway)}).
 *
 * <p><b>Caveat (documented, acceptable for the demo models):</b> the counts are activation-based
 * over the range, not path-traced. A target reachable through more than one inbound flow (e.g. a
 * loop re-entering the branch's first task) counts <em>all</em> its activations, over-attributing
 * that branch — shares can exceed 1 and need not sum to 1. Exact branch shares would need
 * sequence-flow-taken facts, which the pipeline deliberately does not fold.
 */
public record BranchDistribution(
    String gatewayId, String gatewayLabel, long activations, List<Branch> branches) {

  /** One outgoing branch, keyed by the flow's target element. */
  public record Branch(String targetId, String targetLabel, long activations, double share) {}
}
