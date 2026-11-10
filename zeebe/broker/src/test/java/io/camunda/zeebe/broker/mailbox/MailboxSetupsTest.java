/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.mailbox.InMemoryMailboxStore;
import io.camunda.zeebe.mailbox.api.MailboxConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class MailboxSetupsTest {

  private final BrokerCfg brokerCfg = new BrokerCfg();

  @Test
  void shouldHaveNoMailboxWithoutProviders() {
    // when
    final var setup = MailboxSetups.from(List.of(), brokerCfg);

    // then
    assertThat(setup).isEmpty();
  }

  @Test
  void shouldHaveNoMailboxWhenTheProviderSwitchesItOff() {
    // given
    final MailboxSetupProvider off = cfg -> Optional.empty();

    // when
    final var setup = MailboxSetups.from(List.of(off), brokerCfg);

    // then
    assertThat(setup).isEmpty();
  }

  @Test
  void shouldUseTheSetupOfTheProvider() {
    // given
    final var expected = setup();
    final MailboxSetupProvider on = cfg -> Optional.of(expected);

    // when
    final var setup = MailboxSetups.from(List.of(on), brokerCfg);

    // then
    assertThat(setup).containsSame(expected);
  }

  @Test
  void shouldIgnoreProvidersThatSwitchTheMailboxOff() {
    // given
    final var expected = setup();
    final MailboxSetupProvider off = cfg -> Optional.empty();
    final MailboxSetupProvider on = cfg -> Optional.of(expected);

    // when
    final var setup = MailboxSetups.from(List.of(off, on), brokerCfg);

    // then
    assertThat(setup).containsSame(expected);
  }

  @Test
  void shouldRejectTwoProvidersThatBothReturnASetup() {
    // given
    final MailboxSetupProvider first = cfg -> Optional.of(setup());
    final MailboxSetupProvider second = cfg -> Optional.of(setup());

    // when / then
    assertThatThrownBy(() -> MailboxSetups.from(List.of(first, second), brokerCfg))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("at most one");
  }

  @Test
  void shouldHaveNoMailboxWhenNoProviderIsRegisteredOnTheClassPath() {
    // when
    final var setup = MailboxSetups.load(brokerCfg);

    // then
    assertThat(setup).isEmpty();
  }

  private static MailboxSetup setup() {
    return MailboxSetup.of(new InMemoryMailboxStore(), MailboxConfig.defaults(), null);
  }
}
