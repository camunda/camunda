/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import java.util.List;
import java.util.Optional;

/**
 * Checks a change before the coordinator accepts it. A rejected change is never applied, and a dry
 * run reports the same rejection.
 *
 * <p>Unlike a {@link ClusterConfigurationRequestValidator}, which inspects the request, this sees
 * the phases the change was planned into, together with the configuration they will run against,
 * including any other changes still pending in it. It runs on the coordinator when the change is
 * planned, and again against the latest configuration when the change is admitted, so that a change
 * admitted in between is taken into account.
 */
@FunctionalInterface
public interface ClusterConfigurationChangeValidator {

  ClusterConfigurationChangeValidator ACCEPT_ALL = (configuration, phases) -> Optional.empty();

  /**
   * @param configuration the configuration the change will run against
   * @param phases the phases of the change
   * @return the reason the change is rejected, or empty if it is accepted
   */
  Optional<String> validate(CurrentClusterConfiguration configuration, List<Phase> phases);
}
