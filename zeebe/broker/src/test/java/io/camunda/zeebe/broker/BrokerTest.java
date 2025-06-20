/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.unifiedconfig.UnifiedConfiguration;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import org.junit.Rule;
import org.junit.Test;

public final class BrokerTest {

  final UnifiedConfiguration unifiedConfiguration = new UnifiedConfiguration();
  @Rule public final EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule(unifiedConfiguration);

  @Test
  public void shouldStartAndStopBroker() {
    // given broker started
    final Broker broker = brokerRule.getBroker();
    assertThat(broker).isNotNull();

    // when
    brokerRule.stopBroker();

    // then - no error
    assertThat(brokerRule.getBroker()).isNull();
  }
}
