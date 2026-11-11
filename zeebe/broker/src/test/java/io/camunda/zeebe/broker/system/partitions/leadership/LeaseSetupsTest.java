/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.leadership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.mailbox.leadership.InMemoryLeaseStore;
import io.camunda.zeebe.mailbox.leadership.LeaseConfig;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class LeaseSetupsTest {

  private static final LeaseSetup SETUP =
      new LeaseSetup(new InMemoryLeaseStore(), LeaseConfig.of(Duration.ofSeconds(9)));

  @Test
  void shouldTakeLeadersFromRaftWithoutAProvider() {
    // then
    assertThat(LeaseSetups.from(List.of(), new BrokerCfg())).isEmpty();
  }

  @Test
  void shouldUseTheSetupOfTheProviderThatIsOn() {
    // given
    final LeaseSetupProvider off = cfg -> Optional.empty();
    final LeaseSetupProvider on = cfg -> Optional.of(SETUP);

    // then
    assertThat(LeaseSetups.from(List.of(off, on), new BrokerCfg())).contains(SETUP);
  }

  @Test
  void shouldRejectTwoProvidersThatAreOn() {
    // given
    final LeaseSetupProvider on = cfg -> Optional.of(SETUP);

    // then
    assertThatThrownBy(() -> LeaseSetups.from(List.of(on, on), new BrokerCfg()))
        .isInstanceOf(IllegalStateException.class);
  }
}
