/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl.mailbox;

import io.camunda.zeebe.broker.client.api.mailbox.GatewayMailboxSetup;
import io.camunda.zeebe.broker.client.api.mailbox.GatewayMailboxSetupProvider;
import java.util.ArrayList;
import java.util.Optional;
import java.util.ServiceLoader;

/** Finds the mailbox setup of the gateway. */
public final class GatewayMailboxSetups {

  private GatewayMailboxSetups() {}

  /** Asks the providers on the class path. Returns empty if there is none, or none is on. */
  public static Optional<GatewayMailboxSetup> load() {
    return from(ServiceLoader.load(GatewayMailboxSetupProvider.class));
  }

  /**
   * @throws IllegalStateException if more than one provider returns a setup, because the gateway
   *     could not tell which store to use
   */
  static Optional<GatewayMailboxSetup> from(final Iterable<GatewayMailboxSetupProvider> providers) {
    final var setups = new ArrayList<GatewayMailboxSetup>();
    for (final var provider : providers) {
      provider.create().ifPresent(setups::add);
    }
    if (setups.size() > 1) {
      throw new IllegalStateException(
          "Expected at most one gateway mailbox provider to return a setup, but %d did"
              .formatted(setups.size()));
    }
    return setups.stream().findFirst();
  }
}
