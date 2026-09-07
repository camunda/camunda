/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.MessagingException.NoSuchMemberException;
import io.camunda.zeebe.dynamic.config.serializer.ClusterConfigurationRequestsSerializer;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class ClusterConfigurationManagementRequestSenderTest {

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
  private static final MemberId DEFAULT_COORDINATOR = MemberId.from("0");
  private static final MemberId NEXT_COORDINATOR = MemberId.from("1");

  private final ClusterCommunicationService communicationService =
      mock(ClusterCommunicationService.class);
  private final ClusterConfigurationRequestsSerializer serializer =
      mock(ClusterConfigurationRequestsSerializer.class);
  private final CurrentClusterConfiguration expectedTopology =
      mock(CurrentClusterConfiguration.class);

  @Test
  void shouldQueryAnotherMemberWhenTheDefaultCoordinatorIsNotKnown() {
    // given
    doAnswer(
            invocation -> {
              final var coordinator = invocation.<MemberId>getArgument(4);
              if (coordinator.equals(DEFAULT_COORDINATOR)) {
                return CompletableFuture.failedFuture(
                    new NoSuchMemberException("default coordinator is not known"));
              }
              return CompletableFuture.completedFuture(Either.right(expectedTopology));
            })
        .when(communicationService)
        .send(
            eq(ClusterConfigurationRequestTopics.QUERY_TOPOLOGY.topic()),
            any(),
            any(),
            any(),
            any(),
            eq(REQUEST_TIMEOUT));
    final var sender =
        new ClusterConfigurationManagementRequestSender(
            communicationService,
            ClusterConfigurationCoordinatorSupplier.ofMembers(
                Set.of(DEFAULT_COORDINATOR, NEXT_COORDINATOR)),
            serializer,
            MemberId.from("gateway"));

    // when
    final var result = sender.getTopology();

    // then
    assertThat(result)
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(response -> assertThat(response.get()).isSameAs(expectedTopology));
    final var coordinators = ArgumentCaptor.forClass(MemberId.class);
    verify(communicationService, times(2))
        .send(
            eq(ClusterConfigurationRequestTopics.QUERY_TOPOLOGY.topic()),
            any(),
            any(),
            any(),
            coordinators.capture(),
            eq(REQUEST_TIMEOUT));
    assertThat(coordinators.getAllValues()).containsExactly(DEFAULT_COORDINATOR, NEXT_COORDINATOR);
  }
}
