/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.gateway.RequestMapper;
import io.camunda.zeebe.gateway.cmd.InvalidBusinessIdException;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.AssignProcessInstanceBusinessIdRequest;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBusinessIdRecord;
import org.junit.jupiter.api.Test;

public class AssignProcessInstanceBusinessIdTest {

  @Test
  public void shouldMapRequestToAssignProcessInstanceBusinessIdRequest() {
    // given
    final var request =
        AssignProcessInstanceBusinessIdRequest.newBuilder()
            .setProcessInstanceKey(1L)
            .setBusinessId("order-42")
            .build();

    // when
    final ProcessInstanceBusinessIdRecord record =
        RequestMapper.toAssignProcessInstanceBusinessIdRequest(request).getRequestWriter();

    // then
    assertThat(record.getProcessInstanceKey()).isEqualTo(1L);
    assertThat(record.getBusinessId()).isEqualTo("order-42");
  }

  @Test
  public void shouldRejectRequestWhenBusinessIdExceedsMaxLength() {
    // given
    final var tooLongBusinessId = "a".repeat(257);
    final var request =
        AssignProcessInstanceBusinessIdRequest.newBuilder()
            .setProcessInstanceKey(1L)
            .setBusinessId(tooLongBusinessId)
            .build();

    // when / then
    assertThatThrownBy(() -> RequestMapper.toAssignProcessInstanceBusinessIdRequest(request))
        .isInstanceOf(InvalidBusinessIdException.class)
        .hasMessageContaining("exceeds the limit of 256 characters");
  }

  @Test
  public void shouldRejectRequestWhenBusinessIdIsBlank() {
    // given
    final var request =
        AssignProcessInstanceBusinessIdRequest.newBuilder()
            .setProcessInstanceKey(1L)
            .setBusinessId("   ")
            .build();

    // when / then
    assertThatThrownBy(() -> RequestMapper.toAssignProcessInstanceBusinessIdRequest(request))
        .isInstanceOf(InvalidBusinessIdException.class)
        .hasMessageContaining("no business id was provided");
  }
}
