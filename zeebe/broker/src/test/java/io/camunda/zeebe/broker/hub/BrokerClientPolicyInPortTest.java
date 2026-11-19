/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.hub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejectionResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class BrokerClientPolicyInPortTest {

  @Mock private BrokerClient brokerClient;
  @Captor private ArgumentCaptor<BrokerRequest<PolicyUpdateRecord>> requestCaptor;

  @Test
  void shouldAppendTheChunkOnPartitionOneOfThePhysicalTenant() {
    // given
    when(brokerClient.<PolicyUpdateRecord>sendRequestWithRetry(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new BrokerResponse<>(new PolicyUpdateRecord(), Protocol.DEPLOYMENT_PARTITION, 1)));
    final var port = new BrokerClientPolicyInPort("pt-1", brokerClient);

    // when
    final var applied = port.apply(update());

    // then
    assertThat(applied).isCompleted();
    verify(brokerClient).sendRequestWithRetry(requestCaptor.capture());
    final var request = (BrokerPolicyUpdateRequest) requestCaptor.getValue();
    assertThat(request.getPartitionGroup()).isEqualTo("pt-1");
    assertThat(request.getPartitionId()).isEqualTo(Protocol.DEPLOYMENT_PARTITION);
    assertThat(request.getValueType()).isEqualTo(ValueType.POLICY_UPDATE);
    assertThat(request.getIntent()).isEqualTo(PolicyUpdateIntent.APPEND_CHUNK);
    final var chunk = (PolicyUpdateRecord) request.getRequestWriter();
    assertThat(chunk.getUpdateId()).isEqualTo("policy-1-7");
    assertThat(chunk.getSeqNo()).isEqualTo(7);
  }

  @Test
  void shouldFailWhenPartitionOneRejectsTheChunk() {
    // given
    when(brokerClient.<PolicyUpdateRecord>sendRequestWithRetry(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new BrokerRejectionResponse<>(
                    new BrokerRejection(
                        PolicyUpdateIntent.APPEND_CHUNK, 1, RejectionType.INVALID_STATE, "no"))));
    final var port = new BrokerClientPolicyInPort("pt-1", brokerClient);

    // when
    final var applied = port.apply(update());

    // then
    assertThat(applied)
        .failsWithin(Duration.ZERO)
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(BrokerRejectionException.class);
  }

  private static PolicyUpdateRecord update() {
    return new PolicyUpdateRecord()
        .setPolicyId("policy-1")
        .setPolicyVersion(1)
        .setSeqNo(7)
        .setUpdateId("policy-1-7")
        .setChunkIndex(0)
        .setChunkCount(1);
  }
}
