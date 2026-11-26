/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import java.util.List;

/**
 * The answer to a configuration change request.
 *
 * @param changeId the id of the started change; if the request required no change, the id of the
 *     last completed change, or 0 if there is none
 * @param currentConfiguration the configuration before applying the change
 * @param expectedConfiguration the configuration expected once the change has been applied
 * @param phases the phases the change runs, in order; empty if the request required no change
 */
public record ClusterConfigurationChangeResponse(
    long changeId,
    CurrentClusterConfiguration currentConfiguration,
    CurrentClusterConfiguration expectedConfiguration,
    List<Phase> phases) {

  public ClusterConfigurationChangeResponse {
    requireNonNull(currentConfiguration, "currentConfiguration must not be null");
    requireNonNull(expectedConfiguration, "expectedConfiguration must not be null");
    requireNonNull(phases, "phases must not be null");
    phases = List.copyOf(phases);
  }
}
