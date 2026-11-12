/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import java.util.Optional;

/**
 * Checks the configuration a change would produce before the coordinator accepts the change. A
 * rejected change is never applied, and a dry run reports the same rejection.
 *
 * <p>Unlike a {@link ClusterConfigurationRequestValidator}, which inspects the request, this sees
 * the simulated result of the change across all partition groups. It runs on the coordinator, so it
 * can only check against the coordinator's own local resources.
 */
@FunctionalInterface
public interface ClusterConfigurationChangeValidator {

  ClusterConfigurationChangeValidator ACCEPT_ALL = (current, proposed) -> Optional.empty();

  /**
   * @param current the configuration before the change
   * @param proposed the configuration once the change has been fully applied
   * @return the reason the change is rejected, or empty if it is accepted
   */
  Optional<String> validate(
      CurrentClusterConfiguration current, CurrentClusterConfiguration proposed);
}
