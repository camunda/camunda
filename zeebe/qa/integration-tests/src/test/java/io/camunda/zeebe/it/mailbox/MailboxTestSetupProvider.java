/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.mailbox;

import io.camunda.zeebe.broker.mailbox.MailboxSetup;
import io.camunda.zeebe.broker.mailbox.MailboxSetupProvider;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import java.util.Optional;

/** Gives a broker the shared store if a test registered the broker. */
public final class MailboxTestSetupProvider implements MailboxSetupProvider {

  @Override
  public Optional<MailboxSetup> create(final BrokerCfg brokerCfg) {
    final var member = brokerCfg.getCluster().getMemberId();
    return Optional.ofNullable(SharedMailbox.of(member.id()))
        .map(
            shared ->
                MailboxSetup.of(
                    shared.brokerView(member.id()).store(),
                    SharedMailbox.CONFIG,
                    shared.brokerZone(member.id(), member.zone())));
  }
}
