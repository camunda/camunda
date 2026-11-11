/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.leadership;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.mailbox.leadership.LeaseConfig;
import io.camunda.zeebe.mailbox.rdbms.RdbmsMailboxStores;
import java.util.ArrayList;
import java.util.Optional;
import java.util.ServiceLoader;
import org.jspecify.annotations.Nullable;

/** Finds the lease setup of the broker. */
public final class LeaseSetups {

  private LeaseSetups() {}

  /**
   * Asks the providers on the class path first, so that a test can plug in a store of its own, and
   * otherwise takes the leases from the database of the primary storage.
   *
   * @param rdbms the stores in the database of the primary storage, or null if it is not an RDBMS
   * @throws IllegalStateException if the primary storage is an RDBMS but there are no leases. Its
   *     log storage takes the term of the leader from the lease, so the leaders cannot come from
   *     Raft.
   */
  public static Optional<LeaseSetup> load(
      final BrokerCfg brokerCfg,
      final @Nullable RdbmsMailboxStores rdbms,
      final boolean rdbmsPrimaryStorage) {
    return resolve(load(brokerCfg), rdbms, rdbmsPrimaryStorage);
  }

  static Optional<LeaseSetup> resolve(
      final Optional<LeaseSetup> provided,
      final @Nullable RdbmsMailboxStores rdbms,
      final boolean rdbmsPrimaryStorage) {
    final var setup =
        provided.or(
            () ->
                Optional.ofNullable(rdbms)
                    .map(
                        stores ->
                            new LeaseSetup(
                                stores.leaseStore(),
                                LeaseConfig.of(RdbmsMailboxStores.LEASE_DURATION))));
    if (rdbmsPrimaryStorage && setup.isEmpty()) {
      throw new IllegalStateException(
          "Expected the leases of the partitions in the RDBMS primary storage, but there are none;"
              + " the RDBMS log storage cannot take its term from Raft");
    }
    return setup;
  }

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
