/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.client.api.mailbox.GatewayMailboxSetup;
import io.camunda.zeebe.broker.client.api.mailbox.GatewayMailboxSetupProvider;
import io.camunda.zeebe.mailbox.InMemoryMailboxStore;
import io.camunda.zeebe.mailbox.api.MailboxConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class GatewayMailboxSetupsTest {

  private static final GatewayMailboxSetup SETUP =
      new GatewayMailboxSetup(
          new InMemoryMailboxStore(), MailboxConfig.defaults(), "gateway-1", Runnable::run);

  @Test
  void shouldFindNoSetupWithoutProviders() {
    assertThat(GatewayMailboxSetups.from(List.of(), "0")).isEmpty();
  }

  @Test
  void shouldFindNoSetupWhenNoProviderIsOn() {
    // given
    final GatewayMailboxSetupProvider off = memberId -> Optional.empty();

    // when / then
    assertThat(GatewayMailboxSetups.from(List.of(off), "0")).isEmpty();
  }

  @Test
  void shouldUseTheSetupOfTheProviderThatIsOn() {
    // given
    final GatewayMailboxSetupProvider off = memberId -> Optional.empty();
    final GatewayMailboxSetupProvider on = memberId -> Optional.of(SETUP);

    // when / then
    assertThat(GatewayMailboxSetups.from(List.of(off, on), "0")).contains(SETUP);
  }

  @Test
  void shouldGiveTheMemberIdToTheProviders() {
    // given
    final GatewayMailboxSetupProvider byMember =
        memberId -> memberId.equals("east_0") ? Optional.of(SETUP) : Optional.empty();

    // when / then
    assertThat(GatewayMailboxSetups.from(List.of(byMember), "east_0")).contains(SETUP);
    assertThat(GatewayMailboxSetups.from(List.of(byMember), "west_0")).isEmpty();
  }

  @Test
  void shouldFailWhenSeveralProvidersAreOn() {
    // given
    final GatewayMailboxSetupProvider on = memberId -> Optional.of(SETUP);

    // when / then
    assertThatThrownBy(() -> GatewayMailboxSetups.from(List.of(on, on), "0"))
        .isInstanceOf(IllegalStateException.class);
  }
}
