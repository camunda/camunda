/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerExtension;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class CheckpointSchedulingServiceTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  @RegisterExtension
  private final ActorSchedulerExtension actorScheduler = new ActorSchedulerExtension();

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private CheckpointSchedulingService service;

  @BeforeEach
  void setUp() {
    final var membershipService = mock(ClusterMembershipService.class);
    final var localMember = mock(Member.class);
    when(localMember.id()).thenReturn(MemberId.from("0"));
    when(membershipService.getLocalMember()).thenReturn(localMember);
    when(membershipService.getMembers()).thenReturn(Set.of(localMember));
    final var brokerClient = mock(BrokerClient.class);
    final var topologyManager = mock(BrokerTopologyManager.class);
    // a recovering tenant only reschedules, so no request reaches the mocked client
    when(topologyManager.isRecovering(any())).thenReturn(true);
    when(brokerClient.getTopologyManager()).thenReturn(topologyManager);

    service =
        new CheckpointSchedulingService(
            membershipService,
            actorScheduler.get(),
            Map.of("boot", scheduled("PT1H")),
            brokerClient,
            meterRegistry);
    assertThat(actorScheduler.submitActor(service)).succeedsWithin(TIMEOUT);
  }

  @AfterEach
  void tearDown() {
    if (service != null) {
      assertThat(service.closeAsync()).succeedsWithin(TIMEOUT);
    }
  }

  @Test
  void shouldScheduleBackupsOfATenantAddedAtRuntime() {
    // given
    final var tenantId = uniqueTenantId();

    // when
    assertThat(service.putPhysicalTenant(tenantId, scheduled("PT1H"))).succeedsWithin(TIMEOUT);

    // then
    final var actors = service.schedulerActorsByPhysicalTenant();
    assertThat(actors).containsOnlyKeys("boot", tenantId);
    assertThat(actors.get(tenantId)).hasSize(1).noneMatch(Actor::isActorClosed);
    assertThat(metersOf(tenantId)).isNotEmpty();
  }

  @Test
  void shouldReplaceTheSchedulesOfAReplacedTenant() {
    // given
    final var tenantId = uniqueTenantId();
    assertThat(service.putPhysicalTenant(tenantId, scheduled("PT1H"))).succeedsWithin(TIMEOUT);
    final var before = service.schedulerActorsByPhysicalTenant().get(tenantId);

    // when
    assertThat(service.putPhysicalTenant(tenantId, scheduled("PT2H"))).succeedsWithin(TIMEOUT);

    // then
    final var after = service.schedulerActorsByPhysicalTenant().get(tenantId);
    assertThat(before).allMatch(Actor::isActorClosed);
    assertThat(after)
        .hasSize(1)
        .noneMatch(Actor::isActorClosed)
        .doesNotContainAnyElementsOf(before);
    assertThat(metersOf(tenantId)).isNotEmpty();
  }

  @Test
  void shouldStopTheSchedulesOfARemovedTenant() {
    // given
    final var tenantId = uniqueTenantId();
    assertThat(service.putPhysicalTenant(tenantId, scheduled("PT1H"))).succeedsWithin(TIMEOUT);
    final var before = service.schedulerActorsByPhysicalTenant().get(tenantId);

    // when
    assertThat(service.removePhysicalTenant(tenantId)).succeedsWithin(TIMEOUT);

    // then
    assertThat(before).allMatch(Actor::isActorClosed);
    assertThat(service.schedulerActorsByPhysicalTenant()).containsOnlyKeys("boot");
    assertThat(metersOf(tenantId)).isEmpty();
    assertThat(metersOf("boot")).isNotEmpty();
  }

  @Test
  void shouldStopTheSchedulesOfATenantReplacedWithoutASchedule() {
    // given
    final var tenantId = uniqueTenantId();
    assertThat(service.putPhysicalTenant(tenantId, scheduled("PT1H"))).succeedsWithin(TIMEOUT);
    final var before = service.schedulerActorsByPhysicalTenant().get(tenantId);

    // when
    assertThat(service.putPhysicalTenant(tenantId, new BackupCfg())).succeedsWithin(TIMEOUT);

    // then
    assertThat(before).allMatch(Actor::isActorClosed);
    assertThat(service.schedulerActorsByPhysicalTenant()).doesNotContainKey(tenantId);
    assertThat(metersOf(tenantId)).isEmpty();
  }

  private List<Meter> metersOf(final String tenantId) {
    return meterRegistry.getMeters().stream()
        .filter(
            meter ->
                tenantId.equals(meter.getId().getTag(PartitionKeyNames.PHYSICAL_TENANT.asString())))
        .toList();
  }

  private static BackupCfg scheduled(final String schedule) {
    final var backupCfg = new BackupCfg();
    backupCfg.setSchedule(schedule);
    return backupCfg;
  }

  private static String uniqueTenantId() {
    return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }
}
