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
import static org.junit.jupiter.params.provider.Arguments.arguments;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

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
    final var config = zoneAwareConfiguration();

    // when - then
    assertThat(ClusterEndpoint.isZoneAware(config)).isTrue();
  }

  @Test
  void shouldDetectNonZoneAwareCluster() {
    // given
    final var config = nonZoneAwareConfiguration();

    // when - then
    assertThat(ClusterEndpoint.isZoneAware(config)).isFalse();
  }

  // --- POST /brokers/{id} (add broker) ---

  @ParameterizedTest
  @MethodSource("invalidBrokerIdCases")
  void shouldRejectAddBrokerWithInvalidId(
      final ClusterConfigurationManagementRequestSender sender,
      final String id,
      final String errorFragment) {
    // when
    final var response =
        new ClusterEndpoint(sender).add(ClusterEndpoint.Resource.brokers, id, false);

    // then
    assertRejected(response, errorFragment);
    verify(sender, never()).addMembers(any());
  }

  @ParameterizedTest
  @MethodSource("validBrokerIdCases")
  void shouldAllowAddBrokerWithValidId(
      final ClusterConfigurationManagementRequestSender sender, final String id) {
    // given
    when(sender.addMembers(any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(successResponse())));

    // when
    final var response =
        new ClusterEndpoint(sender).add(ClusterEndpoint.Resource.brokers, id, false);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    verify(sender).addMembers(any());
  }

  // --- DELETE /brokers/{id} (remove broker) ---

  @ParameterizedTest
  @MethodSource("invalidBrokerIdCases")
  void shouldRejectRemoveBrokerWithInvalidId(
      final ClusterConfigurationManagementRequestSender sender,
      final String id,
      final String errorFragment) {
    // when
    final var response =
        new ClusterEndpoint(sender).remove(ClusterEndpoint.Resource.brokers, id, false);

    // then
    assertRejected(response, errorFragment);
    verify(sender, never()).removeMembers(any());
  }

  @ParameterizedTest
  @MethodSource("validBrokerIdCases")
  void shouldAllowRemoveBrokerWithValidId(
      final ClusterConfigurationManagementRequestSender sender, final String id) {
    // given
    when(sender.removeMembers(any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(successResponse())));

    // when
    final var response =
        new ClusterEndpoint(sender).remove(ClusterEndpoint.Resource.brokers, id, false);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    final var captor = ArgumentCaptor.forClass(RemoveMembersRequest.class);
    verify(sender).removeMembers(captor.capture());
    assertThat(captor.getValue().members()).containsExactly(MemberId.from(id));
  }

  // --- POST /brokers (scale) ---

  @Test
  void shouldRejectScaleBrokersWithIntegersWhenClusterIsZoneAware() {
    // given
    final var requestSender = mockTopology(zoneAwareConfiguration());
    final var endpoint = new ClusterEndpoint(requestSender);

    // when
    final var response =
        endpoint.scale(
            ClusterEndpoint.Resource.brokers, List.of(0, 1, 2), false, false, Optional.empty());

    // then
    assertRejected(response, "bare node ID", "zone-aware");
    verify(requestSender, never()).scaleMembers(any());
    verify(requestSender, never()).forceScaleDown(any());
  }

  @ParameterizedTest
  @MethodSource("validScaleBrokerIdCases")
  void shouldAllowScaleBrokersWithValidIds(
      final ClusterConfigurationManagementRequestSender sender, final List<Object> ids) {
    // given
    when(sender.scaleMembers(any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(successResponse())));

    // when
    final var response =
        new ClusterEndpoint(sender)
            .scale(ClusterEndpoint.Resource.brokers, ids, false, false, Optional.empty());

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    verify(sender).scaleMembers(any());
  }

  static Stream<Arguments> validScaleBrokerIdCases() {
    return Stream.of(
        arguments(mockTopology(nonZoneAwareConfiguration()), List.of(0, 1, 2)),
        arguments(mockTopology(zoneAwareConfiguration()), List.of("zone-a/0", "zone-b/1")));
  }

  // --- PATCH / (patch cluster with add/remove) ---

  @ParameterizedTest
  @MethodSource("patchClusterRejectionCases")
  void shouldRejectPatchWithInvalidBrokerId(
      final ClusterConfigurationManagementRequestSender sender,
      final ClusterConfigPatchRequest request,
      final String errorFragment) {
    // when
    final var response =
        new ClusterEndpoint(sender).updateClusterConfiguration(false, false, request);

    // then
    assertRejected(response, errorFragment);
    verify(sender, never()).patchCluster(any());
  }

  static Stream<Arguments> patchClusterRejectionCases() {
    return Stream.of(
        arguments(
            mockTopology(zoneAwareConfiguration()),
            new ClusterConfigPatchRequest()
                .brokers(
                    new ClusterConfigPatchRequestBrokers()
                        .add(List.of(BrokerId.of("0"), BrokerId.of("1")))),
            "bare node ID"),
        arguments(
            mockTopology(nonZoneAwareConfiguration()),
            new ClusterConfigPatchRequest()
                .brokers(
                    new ClusterConfigPatchRequestBrokers()
                        .remove(List.of(BrokerId.of("zone-a/0")))),
            "not zone-aware"),
        arguments(
            mockTopology(zoneAwareConfiguration()),
            new ClusterConfigPatchRequest()
                .brokers(
                    new ClusterConfigPatchRequestBrokers()
                        .add(List.of(BrokerId.of(0), BrokerId.of(1)))),
            "bare node ID"));
  }

  @ParameterizedTest
  @MethodSource("patchClusterAllowCases")
  void shouldAllowPatchWithValidBrokerId(
      final ClusterConfigurationManagementRequestSender sender,
      final ClusterConfigPatchRequest request,
      final Function<ClusterPatchRequest, Set<MemberId>> memberExtractor,
      final Set<MemberId> expectedMembers) {
    // given
    when(sender.patchCluster(any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(successResponse())));

    // when
    final var response =
        new ClusterEndpoint(sender).updateClusterConfiguration(false, false, request);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    final var captor = ArgumentCaptor.forClass(ClusterPatchRequest.class);
    verify(sender).patchCluster(captor.capture());
    assertThat(memberExtractor.apply(captor.getValue()))
        .containsExactlyInAnyOrder(expectedMembers.toArray(new MemberId[0]));
  }

  static Stream<Arguments> patchClusterAllowCases() {
    return Stream.of(
        arguments(
            mockTopology(zoneAwareConfiguration()),
            new ClusterConfigPatchRequest()
                .brokers(
                    new ClusterConfigPatchRequestBrokers()
                        .add(List.of(BrokerId.of("zone-a/2"), BrokerId.of("zone-b/3")))),
            (Function<ClusterPatchRequest, Set<MemberId>>) ClusterPatchRequest::membersToAdd,
            Set.of(MemberId.from("zone-a", 2), MemberId.from("zone-b", 3))),
        arguments(
            mockTopology(nonZoneAwareConfiguration()),
            new ClusterConfigPatchRequest()
                .brokers(
                    new ClusterConfigPatchRequestBrokers()
                        .remove(List.of(BrokerId.of("0"), BrokerId.of("1")))),
            (Function<ClusterPatchRequest, Set<MemberId>>) ClusterPatchRequest::membersToRemove,
            Set.of(MemberId.from("0"), MemberId.from("1"))),
        arguments(
            mockTopology(nonZoneAwareConfiguration()),
            new ClusterConfigPatchRequest()
                .brokers(
                    new ClusterConfigPatchRequestBrokers()
                        .add(List.of(BrokerId.of(0), BrokerId.of(1)))),
            (Function<ClusterPatchRequest, Set<MemberId>>) ClusterPatchRequest::membersToAdd,
            Set.of(MemberId.from("0"), MemberId.from("1"))));
  }

  // --- PATCH / with force (force remove brokers) ---

  @Test
  void shouldRejectForceRemoveBrokersWithBareIntegerWhenClusterIsZoneAware() {
    // given
    final var requestSender = mockTopology(zoneAwareConfiguration());
    final var endpoint = new ClusterEndpoint(requestSender);
    final var request =
        new ClusterConfigPatchRequest()
            .brokers(new ClusterConfigPatchRequestBrokers().remove(List.of(BrokerId.of("0"))));

    // when
    final var response = endpoint.updateClusterConfiguration(false, true, request);

    // then
    assertRejected(response, "bare node ID", "zone-aware");
    verify(requestSender, never()).forceRemoveBrokers(any());
  }

  @Test
  void shouldAllowForceRemoveBrokersWithCompositeIdWhenClusterIsZoneAware() {
    // given
    final var requestSender = mockTopology(zoneAwareConfiguration());
    when(requestSender.forceRemoveBrokers(any()))
        .thenReturn(CompletableFuture.completedFuture(Either.right(successResponse())));
    final var endpoint = new ClusterEndpoint(requestSender);
    final var request =
        new ClusterConfigPatchRequest()
            .brokers(
                new ClusterConfigPatchRequestBrokers().remove(List.of(BrokerId.of("zone-a/0"))));

    // when
    final var response = endpoint.updateClusterConfiguration(false, true, request);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    final var captor = ArgumentCaptor.forClass(ForceRemoveBrokersRequest.class);
    verify(requestSender).forceRemoveBrokers(captor.capture());
    assertThat(captor.getValue().membersToRemove()).containsExactly(MemberId.from("zone-a/0"));
  }

  // --- Sub-resource endpoints ---

  @ParameterizedTest
  @MethodSource("subResourceRejectionCases")
  void shouldRejectSubResourceWhenClusterIsZoneAware(
      final Function<ClusterEndpoint, ResponseEntity<?>> operation,
      final Consumer<ClusterConfigurationManagementRequestSender> verifyNeverCalled) {
    // given
    final var sender = mockTopology(zoneAwareConfiguration());

    // when
    final var response = operation.apply(new ClusterEndpoint(sender));

    // then
    assertRejected(response, "zone-aware");
    verifyNeverCalled.accept(sender);
  }

  static Stream<Arguments> subResourceRejectionCases() {
    return Stream.of(
        arguments(
            (Function<ClusterEndpoint, ResponseEntity<?>>)
                ep ->
                    ep.addSubResource(
                        ClusterEndpoint.Resource.brokers,
                        "0",
                        ClusterEndpoint.Resource.partitions,
                        "1",
                        new ClusterEndpoint.PartitionAddRequest(1),
                        false),
            (Consumer<ClusterConfigurationManagementRequestSender>)
                s -> verify(s, never()).joinPartition(any())),
        arguments(
            (Function<ClusterEndpoint, ResponseEntity<?>>)
                ep ->
                    ep.removeSubResource(
                        ClusterEndpoint.Resource.brokers,
                        "0",
                        ClusterEndpoint.Resource.partitions,
                        "1",
                        false),
            (Consumer<ClusterConfigurationManagementRequestSender>)
                s -> verify(s, never()).leavePartition(any())));
  }

  @ParameterizedTest
  @MethodSource("subResourceAllowCases")
  void shouldAllowSubResourceWhenClusterIsNotZoneAware(
      final Consumer<ClusterConfigurationManagementRequestSender> stub,
      final Function<ClusterEndpoint, ResponseEntity<?>> operation,
      final Consumer<ClusterConfigurationManagementRequestSender> verifyWasCalled) {
    // given
    final var sender = mockTopology(nonZoneAwareConfiguration());
    stub.accept(sender);

    // when
    final var response = operation.apply(new ClusterEndpoint(sender));

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    verifyWasCalled.accept(sender);
  }

  static Stream<Arguments> subResourceAllowCases() {
    return Stream.of(
        arguments(
            (Consumer<ClusterConfigurationManagementRequestSender>)
                s ->
                    when(s.joinPartition(any()))
                        .thenReturn(
                            CompletableFuture.completedFuture(Either.right(successResponse()))),
            (Function<ClusterEndpoint, ResponseEntity<?>>)
                ep ->
                    ep.addSubResource(
                        ClusterEndpoint.Resource.brokers,
                        "0",
                        ClusterEndpoint.Resource.partitions,
                        "1",
                        new ClusterEndpoint.PartitionAddRequest(1),
                        false),
            (Consumer<ClusterConfigurationManagementRequestSender>)
                s -> verify(s).joinPartition(any())),
        arguments(
            (Consumer<ClusterConfigurationManagementRequestSender>)
                s ->
                    when(s.leavePartition(any()))
                        .thenReturn(
                            CompletableFuture.completedFuture(Either.right(successResponse()))),
            (Function<ClusterEndpoint, ResponseEntity<?>>)
                ep ->
                    ep.removeSubResource(
                        ClusterEndpoint.Resource.brokers,
                        "0",
                        ClusterEndpoint.Resource.partitions,
                        "1",
                        false),
            (Consumer<ClusterConfigurationManagementRequestSender>)
                s -> verify(s).leavePartition(any())));
  }

  // --- Shared method sources ---

  static Stream<Arguments> invalidBrokerIdCases() {
    return Stream.of(
        arguments(mockTopology(zoneAwareConfiguration()), "0", "bare node ID"),
        arguments(mockTopology(nonZoneAwareConfiguration()), "zone-a/0", "not zone-aware"));
  }

  static Stream<Arguments> validBrokerIdCases() {
    return Stream.of(
        arguments(mockTopology(nonZoneAwareConfiguration()), "0"),
        arguments(mockTopology(zoneAwareConfiguration()), "zone-a/0"));
  }

  // --- Helper methods ---

  private ClusterEndpoint createEndpoint() {
    return new ClusterEndpoint(mock(ClusterConfigurationManagementRequestSender.class));
  }

  private static ClusterConfiguration zoneAwareConfiguration() {
    return ClusterConfiguration.init()
        .addMember(
            MemberId.from("zone-a", 0),
            MemberState.initializeAsActive(
                Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init()))))
        .addMember(
            MemberId.from("zone-b", 0),
            MemberState.initializeAsActive(
                Map.of(1, PartitionState.active(2, DynamicPartitionConfig.init()))));
  }

  private static ClusterConfiguration nonZoneAwareConfiguration() {
    return ClusterConfiguration.init()
        .addMember(
            MemberId.from("0"),
            MemberState.initializeAsActive(
                Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init()))))
        .addMember(
            MemberId.from("1"),
            MemberState.initializeAsActive(
                Map.of(1, PartitionState.active(2, DynamicPartitionConfig.init()))));
  }

  private static ClusterConfigurationManagementRequestSender mockTopology(
      final ClusterConfiguration config) {
    final var requestSender = mock(ClusterConfigurationManagementRequestSender.class);
    when(requestSender.getTopology())
        .thenReturn(CompletableFuture.completedFuture(Either.right(config)));
    return requestSender;
  }

  private static void assertRejected(
      final ResponseEntity<?> response, final String... errorFragments) {
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(((Error) response.getBody()).getMessage()).contains(errorFragments);
  }

  private static ClusterConfigurationChangeResponse successResponse() {
    return new ClusterConfigurationChangeResponse(
        1L,
        Collections.emptySortedMap(),
        Collections.emptySortedMap(),
        List.of(new MemberJoinOperation(MemberId.from("0"))));
  }
}
