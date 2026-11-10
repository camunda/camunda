/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.mailbox;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.client.api.mailbox.GatewayMailboxSetup;
import io.camunda.zeebe.broker.client.api.mailbox.GatewayMailboxSetupProvider;
import java.util.Optional;
import java.util.concurrent.Executors;

/**
 * Gives the gateway of a broker the shared store if a test registered the broker. The gateway acts
 * as if it were in the zone of its broker only.
 */
public final class GatewayMailboxTestSetupProvider implements GatewayMailboxSetupProvider {

  @Override
  public Optional<GatewayMailboxSetup> create(final String memberId) {
    return Optional.ofNullable(SharedMailbox.of(memberId))
        .map(
            shared ->
                new GatewayMailboxSetup(
                    shared.gatewayView(memberId).store(),
                    SharedMailbox.CONFIG,
                    "gateway-" + memberId,
                    Executors.newCachedThreadPool(
                        runnable -> {
                          final var thread = new Thread(runnable, "gateway-mailbox-" + memberId);
                          thread.setDaemon(true);
                          return thread;
                        }),
                    MemberId.from(memberId).zone()));
  }
}
