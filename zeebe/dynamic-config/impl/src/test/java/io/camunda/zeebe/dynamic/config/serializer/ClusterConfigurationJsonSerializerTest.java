/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.serializer;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DependencyChangePlan;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.OperationGraph;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.dynamic.config.state.TenantAvailability;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Round-tripping proves the document loses nothing, but not that it is worth reading. These pin the
 * two rules the serializer teaches Jackson, both of which are about what the document looks like to
 * a person rather than about fidelity.
 */
final class ClusterConfigurationJsonSerializerTest {

  private static final Instant TIMESTAMP = Instant.parse("2026-08-21T10:15:30Z");
  private static final MemberId MEMBER_0 = MemberId.from("0");
  private static final MemberId MEMBER_1 = MemberId.from("1");
  private static final JsonMapper PARSER = JsonMapper.builder().build();

  @Test
  void shouldOmitDerivedHelpers() {
    // given
    final var configuration = configuration(new RoundRobinConfig());

    // when
    final var json = parse(configuration);

    // then — clusterSize() and getMembers() are conclusions drawn from the components below them,
    // not part of the configuration, and a reader would have nowhere to put them
    assertThat(json.propertyNames())
        .containsExactlyInAnyOrder(
            "version", "globalConfiguration", "partitionGroups", "phasedChangeState");
    assertThat(json.at("/globalConfiguration").propertyNames()).doesNotContain("uninitialized");
    assertThat(json.at("/partitionGroups/default").propertyNames())
        .doesNotContain("disabled", "removed");
    // a DependencyChangePlan answers pendingOperations(), completedOperations(), operations() and
    // hasPendingChanges() from the graph it holds, so only the graph itself is state
    assertThat(json.at("/partitionGroups/default/pendingChanges").propertyNames())
        .containsExactlyInAnyOrder("id", "status", "startedAt", "graph", "completed");
  }

  @Test
  void shouldDistinguishVariantsThatShareTheirComponents() {
    // given — a join and a leave operation are indistinguishable by their fields alone
    final var configuration = configuration(new RoundRobinConfig());

    // when
    final var json = parse(configuration);

    // then
    final var operations = json.at("/partitionGroups/default/pendingChanges/graph/operations");
    assertThat(operations.at("/#0/operation/@type").asString()).isEqualTo("PartitionJoinOperation");
    assertThat(operations.at("/#1/operation/@type").asString())
        .isEqualTo("PartitionLeaveOperation");
  }

  @Test
  void shouldNameVariantsThatCarryNoComponents() {
    // given — RoundRobinConfig is an empty record, so the type name is all that identifies it
    final var configuration = configuration(new RoundRobinConfig());

    // when
    final var json = parse(configuration);

    // then
    assertThat(json.at("/globalConfiguration/partitionDistributorConfig/@type").asString())
        .isEqualTo("RoundRobinConfig");
  }

  @Test
  void shouldNotNameConcreteTypes() {
    // given
    final var configuration = configuration(new RoundRobinConfig());

    // when
    final var json = parse(configuration);

    // then — a record's declared type already says what it is, so a name there is only noise
    assertThat(json.propertyNames()).doesNotContain("@type");
    assertThat(json.at("/globalConfiguration/members/0").propertyNames()).doesNotContain("@type");
  }

  @Test
  void shouldRenderOperationIdsTheSameWayInEveryPosition() {
    // given — a sequential graph, so the second operation depends on the first
    final var configuration = configuration(new RoundRobinConfig());

    // when
    final var json = parse(configuration);

    // then — an operation id keys the graph and also appears inside dependsOn, and a reader should
    // not have to recognise two renderings of the same identifier
    final var operations = json.at("/partitionGroups/default/pendingChanges/graph/operations");
    assertThat(operations.propertyNames()).containsExactly("#0", "#1");
    assertThat(operations.at("/#1/dependsOn/0").asString()).isEqualTo("#0");
  }

  @Test
  void shouldKeepTheModelsShape() {
    // given
    final var configuration = configuration(new RoundRobinConfig());

    // when
    final var json = parse(configuration);

    // then — field names and nesting are the model's own, unlike the mapped /actuator/cluster
    // response where these same values appear as a flat list of brokers. Member ids read as
    // strings, both as map keys and as values.
    assertThat(json.at("/globalConfiguration/clusterId").asString()).isEqualTo("test-cluster");
    assertThat(json.at("/globalConfiguration/members").propertyNames()).containsExactly("0");
    assertThat(
            json.at(
                    "/partitionGroups/default/pendingChanges/graph/operations/#0/operation/memberId")
                .asString())
        .isEqualTo("1");
    assertThat(json.at("/partitionGroups/default/members/0/partitions/1/priority").asInt())
        .isEqualTo(1);
  }

  private JsonNode parse(final CurrentClusterConfiguration configuration) {
    return PARSER.readTree(ClusterConfigurationJsonSerializer.toJson(configuration));
  }

  private static CurrentClusterConfiguration configuration(final RoundRobinConfig distributor) {
    final var globalConfiguration =
        new GlobalConfiguration(
            7,
            Optional.of("test-cluster"),
            Map.of(MEMBER_0, new BrokerState(3, TIMESTAMP, BrokerState.State.ACTIVE)),
            Optional.of(distributor),
            Optional.of(
                new DependencyChangePlan(
                    9,
                    Status.IN_PROGRESS,
                    TIMESTAMP,
                    OperationGraph.sequential(List.of(new MemberJoinOperation(MEMBER_1))),
                    new TreeMap<>())),
            Optional.empty());

    final var partitionGroup =
        new PartitionGroupConfiguration(
            5,
            2,
            new TreeMap<>(
                Map.of(
                    MEMBER_0,
                    new BrokerPartitionState(
                        4,
                        TIMESTAMP,
                        Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init())),
                        Mode.PROCESSING))),
            Optional.of(new RoutingState(3, new AllPartitions(2), new HashMod(2))),
            Optional.of(
                new DependencyChangePlan(
                    11,
                    Status.IN_PROGRESS,
                    TIMESTAMP,
                    OperationGraph.sequential(
                        List.of(
                            new PartitionJoinOperation(MEMBER_1, 2, 1, true),
                            new PartitionLeaveOperation(MEMBER_0, 1, 1))),
                    new TreeMap<>())),
            Optional.empty(),
            TenantAvailability.enabled());

    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        globalConfiguration,
        Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, partitionGroup),
        PhasedChangeState.empty());
  }
}
