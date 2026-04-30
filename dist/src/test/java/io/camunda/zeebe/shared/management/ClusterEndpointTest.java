/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterPatchRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceRemoveBrokersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.management.cluster.BrokerId;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestBrokers;
import io.camunda.zeebe.management.cluster.Error;
import io.camunda.zeebe.util.Either;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class ClusterEndpointTest {

  @Test
  void shouldAllowKnownQueryParameters() {
    // given
    final var endpoint = createEndpoint();
    final var request = mock(HttpServletRequest.class);
    when(request.getParameterMap())
        .thenReturn(
            Map.of(
                "dryRun", new String[] {"true"},
                "force", new String[] {"false"},
                "replicationFactor", new String[] {"3"}));

    // when - then
    assertThatCode(() -> endpoint.validateRequestParameters(request)).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectUnknownQueryParameter() {
    // given
    final var endpoint = createEndpoint();
    final var request = mock(HttpServletRequest.class);
    when(request.getParameterMap())
        .thenReturn(Map.of("dryRun", new String[] {"true"}, "unknown", new String[] {"x"}));

    // when - then
    assertThatThrownBy(() -> endpoint.validateRequestParameters(request))
        .hasMessage("Unsupported query parameter(s): unknown");
  }

  @Test
  void shouldRejectAndSortMultipleUnknownQueryParameters() {
    // given
    final var endpoint = createEndpoint();
    final var request = mock(HttpServletRequest.class);
    when(request.getParameterMap())
        .thenReturn(Map.of("x", new String[] {"1"}, "y", new String[] {"2"}));

    // when - then
    assertThatThrownBy(() -> endpoint.validateRequestParameters(request))
        .hasMessage("Unsupported query parameter(s): x, y");
  }

  // --- Zone-awareness detection ---

  @Test
  void shouldDetectZoneAwareCluster() {
    // given
    final var config =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("zone-a", 0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init()))));

    // when - then
    assertThat(ClusterEndpoint.isZoneAware(config)).isTrue();
  }

  @Test
  void shouldDetectNonZoneAwareCluster() {
    // given
    final var config =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("0"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init()))));

    // when - then
    assertThat(ClusterEndpoint.isZoneAware(config)).isFalse();
  }

  // --- POST /brokers/{id} (add broker) ---

  @Test
  void shouldRejectAddBrokerWithBareIntegerWhenClusterIsZoneAware() {
    // given
    final var requestSender = mockZoneAwareTopology();
    final var endpoint = new ClusterEndpoint(requestSender);

    // when
    final var response = endpoint.add(ClusterEndpoint.Resource.brokers, "0", false);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    final var error = (Error) response.getBody();
    assertThat(error.getMessage()).contains("bare node ID").contains("zone-aware");
    verify(requestSender, never()).addMembers(any());
  }

  @Test
  void shouldAllowAddBrokerWithIntegerIdWhenClusterIsNotZoneAware() {
    // given
    final var requestSender = mockNonZoneAwareTopology();
    when(requestSender.addMembers(any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(successResponse())));
    final var endpoint = new ClusterEndpoint(requestSender);

    // when
    final var response = endpoint.add(ClusterEndpoint.Resource.brokers, "0", false);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    verify(requestSender).addMembers(any());
  }

  @Test
  void shouldAllowAddBrokerWithCompositeIdWhenClusterIsZoneAware() {
    // given
    final var requestSender = mockZoneAwareTopology();
    when(requestSender.addMembers(any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(successResponse())));
    final var endpoint = new ClusterEndpoint(requestSender);

    // when
    final var response = endpoint.add(ClusterEndpoint.Resource.brokers, "zone-a/0", false);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    verify(requestSender).addMembers(any());
  }

  @Test
  void shouldRejectAddBrokerWithCompositeIdWhenClusterIsNotZoneAware() {
    // given
    final var requestSender = mockNonZoneAwareTopology();
    final var endpoint = new ClusterEndpoint(requestSender);

    // when
    final var response = endpoint.add(ClusterEndpoint.Resource.brokers, "zone-a/0", false);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    final var error = (Error) response.getBody();
    assertThat(error.getMessage()).contains("not zone-aware").contains("composite member ID");
    verify(requestSender, never()).addMembers(any());
  }

  // --- DELETE /brokers/{id} (remove broker) ---

  @Test
  void shouldRejectRemoveBrokerWithBareIntegerWhenClusterIsZoneAware() {
    // given
    final var requestSender = mockZoneAwareTopology();
    final var endpoint = new ClusterEndpoint(requestSender);

    // when
    final var response = endpoint.remove(ClusterEndpoint.Resource.brokers, "0", false);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    final var error = (Error) response.getBody();
    assertThat(error.getMessage()).contains("bare node ID").contains("zone-aware");
    verify(requestSender, never()).removeMembers(any());
  }

  @Test
  void shouldRejectRemoveBrokerWithCompositeIdWhenClusterIsNotZoneAware() {
    // given
    final var requestSender = mockNonZoneAwareTopology();
    final var endpoint = new ClusterEndpoint(requestSender);

    // when
    final var response = endpoint.remove(ClusterEndpoint.Resource.brokers, "zone-a/0", false);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    final var error = (Error) response.getBody();
    assertThat(error.getMessage()).contains("not zone-aware").contains("composite member ID");
    verify(requestSender, never()).removeMembers(any());
  }

  @Test
  void shouldAllowRemoveBrokerWithBareIntegerWhenClusterIsNotZoneAware() {
    // given
    final var requestSender = mockNonZoneAwareTopology();
    when(requestSender.removeMembers(any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(successResponse())));
    final var endpoint = new ClusterEndpoint(requestSender);

    // when
    final var response = endpoint.remove(ClusterEndpoint.Resource.brokers, "0", false);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    verify(requestSender).removeMembers(any());
  }

  @Test
  void shouldAllowRemoveBrokerWithCompositeIdWhenClusterIsZoneAware() {
    // given
    final var requestSender = mockZoneAwareTopology();
    when(requestSender.removeMembers(any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(successResponse())));
    final var endpoint = new ClusterEndpoint(requestSender);

    // when
    final var response = endpoint.remove(ClusterEndpoint.Resource.brokers, "zone-a/0", false);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    final var captor = ArgumentCaptor.forClass(RemoveMembersRequest.class);
    verify(requestSender).removeMembers(captor.capture());
    assertThat(captor.getValue().members()).containsExactly(MemberId.from("zone-a/0"));
  }

  // --- POST /brokers (scale) ---

  @Test
  void shouldRejectScaleBrokersWithIntegersWhenClusterIsZoneAware() {
    // given
    final var requestSender = mockZoneAwareTopology();
    final var endpoint = new ClusterEndpoint(requestSender);

    // when
    final var response =
        endpoint.scale(
            ClusterEndpoint.Resource.brokers, List.of(0, 1, 2), false, false, Optional.empty());

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    final var error = (Error) response.getBody();
    assertThat(error.getMessage()).contains("bare node ID").contains("zone-aware");
    verify(requestSender, never()).scaleMembers(any());
    verify(requestSender, never()).forceScaleDown(any());
  }

  @Test
  void shouldAllowScaleBrokersWithIntegersWhenClusterIsNotZoneAware() {
    // given
    final var requestSender = mockNonZoneAwareTopology();
    when(requestSender.scaleMembers(any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(successResponse())));
    final var endpoint = new ClusterEndpoint(requestSender);

    // when
    final var response =
        endpoint.scale(
            ClusterEndpoint.Resource.brokers, List.of(0, 1, 2), false, false, Optional.empty());

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    verify(requestSender).scaleMembers(any());
  }

  @Test
  void shouldAllowScaleBrokersWithCompositeIdsWhenClusterIsZoneAware() {
    // given
    final var requestSender = mockZoneAwareTopology();
    when(requestSender.scaleMembers(any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(successResponse())));
    final var endpoint = new ClusterEndpoint(requestSender);

    // when
    final var response =
        endpoint.scale(
            ClusterEndpoint.Resource.brokers,
            List.of("zone-a/0", "zone-b/1"),
            false,
            false,
            Optional.empty());

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    verify(requestSender).scaleMembers(any());
  }

  // --- PATCH / (patch cluster with add/remove) ---

  @Test
  void shouldRejectPatchAddBrokersWithBareIntegerStringWhenClusterIsZoneAware() {
    // given
    final var requestSender = mockZoneAwareTopology();
    final var endpoint = new ClusterEndpoint(requestSender);
    final var request =
        new ClusterConfigPatchRequest()
            .brokers(
                new ClusterConfigPatchRequestBrokers()
                    .add(List.of(new BrokerId.String("0"), new BrokerId.String("1"))));

    // when
    final var response = endpoint.updateClusterConfiguration(false, false, request);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    final var error = (Error) response.getBody();
    assertThat(error.getMessage()).contains("bare node ID").contains("zone-aware");
    verify(requestSender, never()).patchCluster(any());
  }

  @Test
  void shouldRejectPatchRemoveBrokersWithCompositeIdWhenClusterIsNotZoneAware() {
    // given
    final var requestSender = mockNonZoneAwareTopology();
    final var endpoint = new ClusterEndpoint(requestSender);
    final var request =
        new ClusterConfigPatchRequest()
            .brokers(
                new ClusterConfigPatchRequestBrokers()
                    .remove(List.of(new BrokerId.String("zone-a/0"))));

    // when
    final var response = endpoint.updateClusterConfiguration(false, false, request);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    final var error = (Error) response.getBody();
    assertThat(error.getMessage()).contains("not zone-aware").contains("composite member ID");
    verify(requestSender, never()).patchCluster(any());
  }

  @Test
  void shouldAllowPatchAddBrokersWithCompositeIdWhenClusterIsZoneAware() {
    // given
    final var requestSender = mockZoneAwareTopology();
    when(requestSender.patchCluster(any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(successResponse())));
    final var endpoint = new ClusterEndpoint(requestSender);
    final var request =
        new ClusterConfigPatchRequest()
            .brokers(
                new ClusterConfigPatchRequestBrokers()
                    .add(
                        List.of(new BrokerId.String("zone-a/2"), new BrokerId.String("zone-b/3"))));

    // when
    final var response = endpoint.updateClusterConfiguration(false, false, request);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    final var captor = ArgumentCaptor.forClass(ClusterPatchRequest.class);
    verify(requestSender).patchCluster(captor.capture());
    assertThat(captor.getValue().membersToAdd())
        .containsExactlyInAnyOrder(MemberId.from("zone-a", 2), MemberId.from("zone-b", 3));
  }

  @Test
  void shouldAllowPatchRemoveBrokersWithBareIntegerStringWhenClusterIsNotZoneAware() {
    // given
    final var requestSender = mockNonZoneAwareTopology();
    when(requestSender.patchCluster(any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(successResponse())));
    final var endpoint = new ClusterEndpoint(requestSender);
    final var request =
        new ClusterConfigPatchRequest()
            .brokers(
                new ClusterConfigPatchRequestBrokers()
                    .remove(List.of(new BrokerId.String("0"), new BrokerId.String("1"))));

    // when
    final var response = endpoint.updateClusterConfiguration(false, false, request);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    final var captor = ArgumentCaptor.forClass(ClusterPatchRequest.class);
    verify(requestSender).patchCluster(captor.capture());
    assertThat(captor.getValue().membersToRemove())
        .containsExactlyInAnyOrder(MemberId.from("0"), MemberId.from("1"));
  }

  // --- Backward compatibility: integer values in PATCH add/remove ---

  @Test
  void shouldAcceptIntegersInPatchAddBrokersWhenClusterIsNotZoneAware() {
    // given
    final var requestSender = mockNonZoneAwareTopology();
    when(requestSender.patchCluster(any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(successResponse())));
    final var endpoint = new ClusterEndpoint(requestSender);
    final var request =
        new ClusterConfigPatchRequest()
            .brokers(
                new ClusterConfigPatchRequestBrokers()
                    .add(List.of(new BrokerId.Integer(0), new BrokerId.Integer(1))));

    // when
    final var response = endpoint.updateClusterConfiguration(false, false, request);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    final var captor = ArgumentCaptor.forClass(ClusterPatchRequest.class);
    verify(requestSender).patchCluster(captor.capture());
    assertThat(captor.getValue().membersToAdd())
        .containsExactlyInAnyOrder(MemberId.from("0"), MemberId.from("1"));
  }

  @Test
  void shouldRejectIntegersInPatchAddBrokersWhenClusterIsZoneAware() {
    // given
    final var requestSender = mockZoneAwareTopology();
    final var endpoint = new ClusterEndpoint(requestSender);
    final var request =
        new ClusterConfigPatchRequest()
            .brokers(
                new ClusterConfigPatchRequestBrokers()
                    .add(List.of(new BrokerId.Integer(0), new BrokerId.Integer(1))));

    // when
    final var response = endpoint.updateClusterConfiguration(false, false, request);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    final var error = (Error) response.getBody();
    assertThat(error.getMessage()).contains("bare node ID").contains("zone-aware");
    verify(requestSender, never()).patchCluster(any());
  }

  // --- PATCH / with force (force remove brokers) ---

  @Test
  void shouldRejectForceRemoveBrokersWithBareIntegerWhenClusterIsZoneAware() {
    // given
    final var requestSender = mockZoneAwareTopology();
    final var endpoint = new ClusterEndpoint(requestSender);
    final var request =
        new ClusterConfigPatchRequest()
            .brokers(
                new ClusterConfigPatchRequestBrokers().remove(List.of(new BrokerId.String("0"))));

    // when
    final var response = endpoint.updateClusterConfiguration(false, true, request);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    final var error = (Error) response.getBody();
    assertThat(error.getMessage()).contains("bare node ID").contains("zone-aware");
    verify(requestSender, never()).forceRemoveBrokers(any());
  }

  @Test
  void shouldAllowForceRemoveBrokersWithCompositeIdWhenClusterIsZoneAware() {
    // given
    final var requestSender = mockZoneAwareTopology();
    when(requestSender.forceRemoveBrokers(any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(successResponse())));
    final var endpoint = new ClusterEndpoint(requestSender);
    final var request =
        new ClusterConfigPatchRequest()
            .brokers(
                new ClusterConfigPatchRequestBrokers()
                    .remove(List.of(new BrokerId.String("zone-a/0"))));

    // when
    final var response = endpoint.updateClusterConfiguration(false, true, request);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    final var captor = ArgumentCaptor.forClass(ForceRemoveBrokersRequest.class);
    verify(requestSender).forceRemoveBrokers(captor.capture());
    assertThat(captor.getValue().membersToRemove()).containsExactly(MemberId.from("zone-a/0"));
  }

  // --- Sub-resource endpoints ---

  @Test
  void shouldRejectAddSubResourceWhenClusterIsZoneAware() {
    // given
    final var requestSender = mockZoneAwareTopology();
    final var endpoint = new ClusterEndpoint(requestSender);

    // when
    final var response =
        endpoint.addSubResource(
            ClusterEndpoint.Resource.brokers,
            "0",
            ClusterEndpoint.Resource.partitions,
            "1",
            new ClusterEndpoint.PartitionAddRequest(1),
            false);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    final var error = (Error) response.getBody();
    assertThat(error.getMessage()).contains("zone-aware");
    verify(requestSender, never()).joinPartition(any());
  }

  @Test
  void shouldRejectRemoveSubResourceWhenClusterIsZoneAware() {
    // given
    final var requestSender = mockZoneAwareTopology();
    final var endpoint = new ClusterEndpoint(requestSender);

    // when
    final var response =
        endpoint.removeSubResource(
            ClusterEndpoint.Resource.brokers, "0", ClusterEndpoint.Resource.partitions, "1", false);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    final var error = (Error) response.getBody();
    assertThat(error.getMessage()).contains("zone-aware");
    verify(requestSender, never()).leavePartition(any());
  }

  @Test
  void shouldAllowAddSubResourceWhenClusterIsNotZoneAware() {
    // given
    final var requestSender = mockNonZoneAwareTopology();
    when(requestSender.joinPartition(any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(successResponse())));
    final var endpoint = new ClusterEndpoint(requestSender);

    // when
    final var response =
        endpoint.addSubResource(
            ClusterEndpoint.Resource.brokers,
            "0",
            ClusterEndpoint.Resource.partitions,
            "1",
            new ClusterEndpoint.PartitionAddRequest(1),
            false);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    verify(requestSender).joinPartition(any());
  }

  @Test
  void shouldAllowRemoveSubResourceWhenClusterIsNotZoneAware() {
    // given
    final var requestSender = mockNonZoneAwareTopology();
    when(requestSender.leavePartition(any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(successResponse())));
    final var endpoint = new ClusterEndpoint(requestSender);

    // when
    final var response =
        endpoint.removeSubResource(
            ClusterEndpoint.Resource.brokers, "0", ClusterEndpoint.Resource.partitions, "1", false);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    verify(requestSender).leavePartition(any());
  }

  // --- Helper methods ---

  private ClusterEndpoint createEndpoint() {
    return new ClusterEndpoint(mock(ClusterConfigurationManagementRequestSender.class));
  }

  private static ClusterConfigurationManagementRequestSender mockZoneAwareTopology() {
    final var requestSender = mock(ClusterConfigurationManagementRequestSender.class);
    final var config =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("zone-a", 0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init()))))
            .addMember(
                MemberId.from("zone-b", 0),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(2, DynamicPartitionConfig.init()))));
    when(requestSender.getTopology())
        .thenReturn(CompletableFuture.completedFuture(Either.right(config)));
    return requestSender;
  }

  private static ClusterConfigurationManagementRequestSender mockNonZoneAwareTopology() {
    final var requestSender = mock(ClusterConfigurationManagementRequestSender.class);
    final var config =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("0"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init()))))
            .addMember(
                MemberId.from("1"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(2, DynamicPartitionConfig.init()))));
    when(requestSender.getTopology())
        .thenReturn(CompletableFuture.completedFuture(Either.right(config)));
    return requestSender;
  }

  private static ClusterConfigurationChangeResponse successResponse() {
    return new ClusterConfigurationChangeResponse(
        1L,
        Collections.emptySortedMap(),
        Collections.emptySortedMap(),
        List.of(new MemberJoinOperation(MemberId.from("0"))));
  }
}
