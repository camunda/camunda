/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.Protocol;
import org.junit.jupiter.api.Test;

final class BrokerActivateAdHocSubProcessActivityRequestTest {

  @Test
  void shouldAddressPartitionOfAdHocSubProcessInstance() {
    // given
    final int partitionId = 3;
    final long adHocSubProcessInstanceKey = Protocol.encodePartitionId(partitionId, 4_242L);

    // when
    final var request =
        new BrokerActivateAdHocSubProcessActivityRequest()
            .setAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey);

    // then
    assertThat(request.addressesSpecificPartition()).isTrue();
    assertThat(request.getPartitionId()).isEqualTo(partitionId);
    assertThat(request.getRequestWriter().getAdHocSubProcessInstanceKey())
        .isEqualTo(adHocSubProcessInstanceKey);
  }
}
