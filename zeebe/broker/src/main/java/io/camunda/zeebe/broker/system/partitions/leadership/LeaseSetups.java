/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.leadership;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import java.util.ArrayList;
import java.util.Optional;
import java.util.ServiceLoader;

/** Finds the lease setup of the broker. */
public final class LeaseSetups {

  private LeaseSetups() {}

  /** Asks the providers on the class path. Returns empty if there is none, or none is on. */
  public static Optional<LeaseSetup> load(final BrokerCfg brokerCfg) {
    return from(ServiceLoader.load(LeaseSetupProvider.class), brokerCfg);
  }

  /**
   * @throws IllegalStateException if more than one provider returns a setup
   */
  static Optional<LeaseSetup> from(
      final Iterable<LeaseSetupProvider> providers, final BrokerCfg brokerCfg) {
    final var setups = new ArrayList<LeaseSetup>();
    for (final var provider : providers) {
      provider.create(brokerCfg).ifPresent(setups::add);
    }
    if (setups.size() > 1) {
      throw new IllegalStateException(
          "Expected at most one lease provider to return a setup, but %d did"
              .formatted(setups.size()));
    }
    return setups.stream().findFirst();
  }
}
