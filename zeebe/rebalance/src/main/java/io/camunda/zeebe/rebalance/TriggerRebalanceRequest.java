/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

/**
 * Asks the coordinator to start a cluster-wide rebalance.
 *
 * @param overrides any overrides for rebalance settings applying to this run
 * @param dryRun run pre-checks and report the plan without pausing or transferring anything
 */
public record TriggerRebalanceRequest(RebalanceOverrides overrides, boolean dryRun) {
  public static TriggerRebalanceRequest withConfiguredSettings() {
    return new TriggerRebalanceRequest(RebalanceOverrides.none(), false);
  }
}
