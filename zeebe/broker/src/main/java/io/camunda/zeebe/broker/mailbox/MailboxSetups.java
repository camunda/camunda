/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.mailbox;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.mailbox.api.MailboxConfig;
import io.camunda.zeebe.mailbox.rdbms.RdbmsMailboxStores;
import java.util.ArrayList;
import java.util.Optional;
import java.util.ServiceLoader;
import org.jspecify.annotations.Nullable;

/** Finds the mailbox setup of the broker. */
public final class MailboxSetups {

  private MailboxSetups() {}

  /**
   * Asks the providers on the class path first, so that a test can plug in a store of its own, and
   * otherwise uses the mailbox in the database of the primary storage.
   *
   * @param rdbms the stores in the database of the primary storage, or null if it is not an RDBMS
   */
  public static Optional<MailboxSetup> load(
      final BrokerCfg brokerCfg, final @Nullable RdbmsMailboxStores rdbms) {
    return resolve(load(brokerCfg), rdbms);
  }

  static Optional<MailboxSetup> resolve(
      final Optional<MailboxSetup> provided, final @Nullable RdbmsMailboxStores rdbms) {
    return provided.or(
        () ->
            Optional.ofNullable(rdbms)
                .map(
                    stores ->
                        MailboxSetup.of(stores.mailboxStore(), MailboxConfig.defaults(), null)));
  }

  /** Asks the providers on the class path. Returns empty if there is none, or none is on. */
  public static Optional<MailboxSetup> load(final BrokerCfg brokerCfg) {
    return from(ServiceLoader.load(MailboxSetupProvider.class), brokerCfg);
  }

  /**
   * @throws IllegalStateException if more than one provider returns a setup, because the broker
   *     could not tell which store to use
   */
  static Optional<MailboxSetup> from(
      final Iterable<MailboxSetupProvider> providers, final BrokerCfg brokerCfg) {
    final var setups = new ArrayList<MailboxSetup>();
    for (final var provider : providers) {
      provider.create(brokerCfg).ifPresent(setups::add);
    }
    if (setups.size() > 1) {
      throw new IllegalStateException(
          "Expected at most one mailbox provider to return a setup, but %d did"
              .formatted(setups.size()));
    }
    return setups.stream().findFirst();
  }
}
