/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration.DEFAULT_GROUP;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.BARE_0;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.BARE_1;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.BARE_2;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterPatchRequestTransformer;
import io.camunda.zeebe.dynamic.config.api.ForceRemoveBrokersRequestTransformer;
import io.camunda.zeebe.dynamic.config.api.RemovePhysicalTenantRequestTransformer;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor.NoopClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeResult;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinatorImpl;
import io.camunda.zeebe.dynamic.config.changes.GlobalConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor.NoopModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopPartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor.NoopPartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.RestoreChangeExecutor.NoopRestoreChangeExecutor;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that a broker holding a <em>disabled</em> physical tenant's partitions cannot leave the
 * cluster, by any route, until that tenant has been explicitly removed: the coordinator excludes a
 * disabled tenant's partitions from every plan, yet {@code MemberLeaveApplier} still refuses a
 * member leave while any group — including a disabled one — still assigns it partitions.
 *
 * <p>Exercised through the coordinator ({@link #simulate}/{@link #apply}) rather than by inspecting
 * a transformer's output directly, since the refusal only appears once the plan runs through the
 * validating appliers.
 *
 * <p>The control case runs the same removal with the tenant enabled, to isolate the disabled flag
 * as the cause rather than multi-tenancy in general.
 */
final class PhysicalTenantDisabledRemovalTest {

  private static final String TENANT_A = "tenanta";
  private static final int PARTITION_ID = 1;

  private final TestConcurrencyControl executor = new TestConcurrencyControl();
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @TempDir private Path tmp;

  private ClusterConfigurationManagerImpl manager;
  private ConfigurationChangeCoordinatorImpl coordinator;

  /**
   * The graceful route, {@code PATCH /actuator/cluster} with {@code brokers.remove}. Broker 2 holds
   * nothing of the default tenant, so the only thing standing between it and the door is the
   * disabled tenant's assignment.
   */
  @Test
  void shouldRejectGracefulBrokerRemovalWhenADisabledTenantIsAssignedToIt() {
    // given — three brokers, and only the disabled tenant is replicated on broker 2
    wire(BARE_0, disable(TENANT_A, twoTenants()));

    // when — broker 2 is asked to leave gracefully
    final var result =
        simulate(
            new ClusterPatchRequestTransformer(
                Set.of(), Set.of(BARE_2), Optional.empty(), Optional.empty()));

    // then — the member leave refuses the whole plan, naming the tenant that is in the way but not
    // that it is disabled, nor what the operator is expected to do about it
    assertThat(result)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("still has partitions assigned")
        .withMessageContaining(TENANT_A);
  }

  /**
   * The forced route, {@code POST /actuator/cluster/brokers?force=true}. This is the case that has
   * no graceful alternative: an operator reaches for it once the broker is already gone, so
   * re-enabling the tenant to move its partitions off is not on the table.
   */
  @Test
  void shouldRejectForcedBrokerRemovalWhenADisabledTenantIsAssignedToIt() {
    // given — the same cluster, with the same tenant disabled
    wire(BARE_0, disable(TENANT_A, twoTenants()));

    // when — broker 2 is force-removed, as it would be after failing
    final var result = simulate(new ForceRemoveBrokersRequestTransformer(Set.of(BARE_2), BARE_0));

    // then — the recovery path is refused for the same reason as the graceful one
    assertThat(result)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("still has partitions assigned")
        .withMessageContaining(TENANT_A);
  }

  /**
   * The control: with the tenant enabled the very same request succeeds, because the plan is free
   * to move the tenant's partition off the departing broker first.
   */
  @Test
  void shouldGracefullyRemoveABrokerWhenTheTenantIsEnabled() {
    // given — the same cluster, with no tenant disabled
    wire(BARE_0, twoTenants());

    // when — broker 2 is asked to leave gracefully
    final var configuration =
        simulate(
                new ClusterPatchRequestTransformer(
                    Set.of(), Set.of(BARE_2), Optional.empty(), Optional.empty()))
            .join()
            .finalMultiConfiguration();

    // then — the tenant's partition has moved to the retained brokers and broker 2 has left
    assertThat(replicasOf(configuration, TENANT_A))
        .describedAs("replicas of tenant '%s' partition", TENANT_A)
        .containsExactlyInAnyOrder(BARE_0, BARE_1);
    assertThat(configuration.globalConfiguration().members())
        .describedAs("brokers left in the cluster")
        .containsOnlyKeys(BARE_0, BARE_1);
  }

  /**
   * Once the operator has discarded the disabled tenant, the broker holding its partitions can
   * leave.
   */
  @Test
  void shouldGracefullyRemoveABrokerAfterTheDisabledTenantIsRemoved() {
    // given — a disabled tenant whose partitions sit on broker 2, explicitly discarded
    wire(BARE_0, disable(TENANT_A, twoTenants()));
    apply(new RemovePhysicalTenantRequestTransformer(TENANT_A, BARE_0, false));

    // when — broker 2 is asked to leave gracefully, exactly as it was refused before
    final var configuration =
        simulate(
                new ClusterPatchRequestTransformer(
                    Set.of(), Set.of(BARE_2), Optional.empty(), Optional.empty()))
            .join()
            .finalMultiConfiguration();

    // then — the broker is gone, and the discarded tenant's old assignment is cleared rather than
    // carried forward: only the tombstone itself remains
    assertThat(configuration.globalConfiguration().members())
        .describedAs("brokers left in the cluster")
        .containsOnlyKeys(BARE_0, BARE_1);
    assertThat(configuration.partitionGroup(TENANT_A).isRemoved())
        .describedAs("tenant '%s' is tombstoned as discarded", TENANT_A)
        .isTrue();
    assertThat(replicasOf(configuration, TENANT_A))
        .describedAs("assignment of the discarded tenant '%s'", TENANT_A)
        .isEmpty();
  }

  /**
   * The disaster scenario a forced request exists for: the coordinator is unreachable, so the
   * removal is executed by whichever broker actually received the request instead. Here that is
   * broker 2, not the coordinator, and the removal still completes on it.
   */
  @Test
  void shouldRemoveADisabledTenantOnWhicheverBrokerReceivedTheRequest() {
    // given — broker 2, not the coordinator (broker 0), receives the request
    wire(BARE_2, disable(TENANT_A, twoTenants()));

    // when — the request is forced, naming broker 2 itself as the executing member
    apply(new RemovePhysicalTenantRequestTransformer(TENANT_A, BARE_2, true));

    // then — the removal completes locally on broker 2 despite it not being the coordinator
    assertThat(configuration().partitionGroup(TENANT_A).isRemoved())
        .describedAs("tenant '%s' is tombstoned as discarded", TENANT_A)
        .isTrue();
  }

  /**
   * The normal case, mirroring the disaster scenario above: without {@code force}, a removal
   * received by a broker that does not hold the election is rejected exactly like any other
   * configuration change, rather than silently executing wherever it happened to land.
   */
  @Test
  void shouldRejectANonForcedRemovalReceivedByABrokerThatIsNotTheCoordinator() {
    // given — broker 2, not the coordinator (broker 0), receives the request
    wire(BARE_2, disable(TENANT_A, twoTenants()));

    // when — the request is not forced, so broker 2 must be the coordinator to execute it
    final var result =
        simulate(new RemovePhysicalTenantRequestTransformer(TENANT_A, BARE_2, false));

    // then — the request is rejected because broker 2 does not hold the election
    assertThat(result)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("is not the coordinator");
  }

  /**
   * A removal only makes sense for a tenant that is out of configuration; an enabled one is a typo.
   */
  @Test
  void shouldRejectRemovingAnEnabledPhysicalTenant() {
    // given — the tenant is still running
    wire(BARE_0, twoTenants());

    // when / then — the request is refused, and says how to make it valid
    assertThat(simulate(new RemovePhysicalTenantRequestTransformer(TENANT_A, BARE_0, false)))
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("still enabled");
  }

  @Test
  void shouldRejectRemovingAnUnknownPhysicalTenant() {
    // given
    wire(BARE_0, twoTenants());

    // when / then
    assertThat(simulate(new RemovePhysicalTenantRequestTransformer("nosuchtenant", BARE_0, false)))
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("no such tenant");
  }

  /**
   * A removal must survive a merge with a peer that has not seen it yet; see {@link
   * io.camunda.zeebe.dynamic.config.state.TenantAvailability}.
   */
  @Test
  void shouldKeepTheRemovalWhenMergingWithAPeerThatHasNotSeenIt() {
    // given — a configuration in which the tenant has been discarded, and the stale peer it came
    // from
    wire(BARE_0, disable(TENANT_A, twoTenants()));
    final var stale = configuration();
    apply(new RemovePhysicalTenantRequestTransformer(TENANT_A, BARE_0, false));
    final var removed = configuration();

    // when — the two are merged in both directions
    // then — the removal survives either way, and the tenant's group is still there to be merged
    assertThat(removed.merge(stale).partitionGroup(TENANT_A).isRemoved())
        .describedAs("removal merged against a stale peer")
        .isTrue();
    assertThat(stale.merge(removed).partitionGroup(TENANT_A).isRemoved())
        .describedAs("stale peer merged against the removal")
        .isTrue();
  }

  /** Applies a request for real, so its effect is in the configuration the next request reads. */
  private void apply(final ConfigurationChangeRequest request) {
    coordinator.applyOperations(request).join();
  }

  private CurrentClusterConfiguration configuration() {
    return coordinator.getClusterConfiguration().join();
  }

  /**
   * A dry run rather than a real apply: applying would drive each operation on the broker named by
   * it, and a forced removal's brokers are by definition not running. The dry run still puts the
   * whole plan through the real appliers — the member leave among them — so it fails here exactly
   * as it does on a live cluster.
   */
  private ActorFuture<ConfigurationChangeResult> simulate(
      final ConfigurationChangeRequest request) {
    return coordinator.simulateOperations(request);
  }

  /** The brokers replicating the given physical tenant's only partition. */
  private Set<MemberId> replicasOf(
      final CurrentClusterConfiguration configuration, final String physicalTenantId) {
    return configuration.partitionGroup(physicalTenantId).members().entrySet().stream()
        .filter(member -> member.getValue().partitions().containsKey(PARTITION_ID))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  private CurrentClusterConfiguration disable(
      final String physicalTenantId, final CurrentClusterConfiguration configuration) {
    return configuration.updatePartitionGroupConfig(
        physicalTenantId, PartitionGroupConfiguration::disable);
  }

  /**
   * Three brokers running two tenants, placed so that broker 2 holds only tenant A's partition:
   * removing it is a no-op for the default tenant, which leaves the disabled tenant as the sole
   * reason the removal can fail.
   */
  private CurrentClusterConfiguration twoTenants() {
    final var base = CurrentClusterConfiguration.fromLegacy(cluster(Set.of(BARE_0, BARE_1)));
    return new CurrentClusterConfiguration(
        base.version(),
        base.globalConfiguration(),
        Map.of(
            DEFAULT_GROUP,
            base.partitionGroup(DEFAULT_GROUP),
            TENANT_A,
            CurrentClusterConfiguration.fromLegacy(cluster(Set.of(BARE_1, BARE_2)))
                .partitionGroup(DEFAULT_GROUP)),
        base.phasedChangeState());
  }

  /**
   * A three-broker cluster in which {@code replicas} replicate partition 1. The member set is
   * cluster-wide, so it is the same for both tenants however their partitions are placed.
   */
  private ClusterConfiguration cluster(final Set<MemberId> replicas) {
    var topology = ClusterConfiguration.init();
    for (final var member : Set.of(BARE_0, BARE_1, BARE_2)) {
      topology = topology.addMember(member, MemberState.initializeAsActive(Map.of()));
    }
    var priority = replicas.size();
    for (final var replica : replicas) {
      final var state = PartitionState.active(priority--, partitionConfig);
      topology = topology.updateMember(replica, member -> member.addPartition(PARTITION_ID, state));
    }
    return topology;
  }

  /**
   * Registers change appliers only for the tenants that are enabled, which is what a broker does: a
   * tenant absent from local static configuration — the reason it is disabled in the first place —
   * has its appliers deregistered, and that is why no operation targeting it can ever complete.
   */
  private void wire(final MemberId localMemberId, final CurrentClusterConfiguration seed) {
    manager =
        new ClusterConfigurationManagerImpl(
            executor,
            localMemberId,
            PersistedCurrentClusterConfiguration.ofFile(
                tmp.resolve("config.meta"), new ProtoBufSerializer()),
            new TopologyManagerMetrics(new SimpleMeterRegistry()),
            Duration.ofMillis(1),
            Duration.ofMillis(1));
    manager.setCurrentConfigurationGossiper(ignored -> {});
    manager.registerGlobalChangeAppliers(
        new GlobalConfigurationChangeAppliersImpl(
            new NoopClusterMembershipChangeExecutor(), new NoopClusterChangeExecutor()));
    seed.activePartitionGroups()
        .keySet()
        .forEach(
            groupId ->
                manager.registerPartitionGroupChangeAppliers(
                    groupId,
                    new PartitionGroupConfigurationChangeAppliersImpl(
                        new NoopPartitionChangeExecutor(),
                        new NoopPartitionScalingChangeExecutor(),
                        new NoopModeChangeExecutor(),
                        new NoopRestoreChangeExecutor())));
    coordinator = new ConfigurationChangeCoordinatorImpl(manager, localMemberId, executor);
    manager.updateMultiConfiguration(ignored -> seed).join();
  }
}
