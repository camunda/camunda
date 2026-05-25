/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.admin;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.BrokerMemberId;
import org.junit.jupiter.api.Test;

class BrokerAdminRequestTest {

  @Test
  void shouldReturnZoneAwareBrokerId() {
    // given
    final var brokerId = BrokerMemberId.from("zone-a", 1);
    final var request = new BrokerAdminRequest();

    // when
    request.setBrokerId(brokerId);

    // then
    assertThat(request.getBrokerId()).contains(brokerId);
  }
}
