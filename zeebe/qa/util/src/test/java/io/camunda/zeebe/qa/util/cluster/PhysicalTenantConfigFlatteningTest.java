/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A physical tenant declared via {@link TestSpringApplication#withPtConfig} is emitted as {@code
 * camunda.physical-tenants.<id>.*} properties, and the broker resolves it by binding {@code
 * camunda.*} into a fresh instance and only then overlaying those keys ({@code
 * PhysicalTenantResolver}). An omitted tenant key therefore resolves to the <em>root's</em> value,
 * which is what these tests pin: a tenant emits exactly the fields it changes relative to the root,
 * no more and no less.
 */
@SuppressWarnings("resource")
final class PhysicalTenantConfigFlatteningTest {

  private static final String TENANT = "tenanta";
  private static final String ABSENT = "<absent>";

  @Test
  void shouldEmitTenantValueThatEqualsThePristineDefaultButNotTheRoot() {
    // given - a root with 2 partitions, and a tenant that wants exactly 1. 1 is also the pristine
    // Camunda default, so a tenant flattened against a pristine baseline would emit nothing here
    // and silently inherit the root's 2
    final var broker =
        new TestStandaloneBroker()
            .withClusterConfig(cluster -> cluster.setPartitionCount(2))
            .withPtConfig(TENANT, camunda -> camunda.getCluster().setPartitionCount(1));

    // when
    broker.createSpringBuilder();

    // then
    assertThat(tenantProperty(broker, "cluster.partition-count"))
        .as("the declared partition count reaches the broker rather than being inherited")
        .isEqualTo(1);
  }

  @Test
  void shouldStillDeclareAValueTheModifierCopiedFromTheRoot() {
    // given - a tenant that sets a value identical to the root's. CamundaMultiDBExtension does
    // exactly this with security.initialization, because a tenant must own that block and may not
    // inherit it (PhysicalTenantRequiredOverrideValidation) - so the declaration must survive even
    // though it changes nothing
    final var broker =
        new TestStandaloneBroker()
            .withClusterConfig(cluster -> cluster.setPartitionCount(3))
            .withPtConfig(TENANT, camunda -> camunda.getCluster().setPartitionCount(3));

    // when
    broker.createSpringBuilder();

    // then
    assertThat(tenantProperty(broker, "cluster.partition-count"))
        .as("a value the modifier set is declared even when it equals the root's")
        .isEqualTo(3);
  }

  @Test
  void shouldRejectATenantThatEmitsNoProperty() {
    // given - a tenant that overrides nothing at all, so nothing is emitted under
    // camunda.physical-tenants.<id> and PhysicalTenantResolver, which discovers tenants from
    // exactly those keys, would never see it
    final var broker = new TestStandaloneBroker().withPtConfig(TENANT, camunda -> {});

    // when / then - fail here rather than silently start a broker without the tenant
    assertThatThrownBy(broker::createSpringBuilder)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(TENANT)
        .hasMessageContaining("differs from the root");
  }

  @Test
  void shouldNotEmitRootValuesTheTenantNeverOverrode() {
    // given - a root that moves several cluster-wide fields away from their pristine defaults,
    // and a tenant that overrides only the partition count. Those fields are non-overridable per
    // tenant (PhysicalTenantOverridePolicyValidation), so emitting them would fail resolution
    // outright rather than merely being redundant
    final var broker =
        new TestStandaloneBroker()
            .withClusterConfig(
                cluster -> {
                  cluster.setPartitionCount(2);
                  cluster.setReplicationFactor(3);
                  cluster.setSize(3);
                  cluster.setName("some-cluster");
                })
            .withPtConfig(TENANT, camunda -> camunda.getCluster().setPartitionCount(1));

    // when
    broker.createSpringBuilder();

    // then
    assertThat(tenantProperty(broker, "cluster.size")).isEqualTo(ABSENT);
    assertThat(tenantProperty(broker, "cluster.name")).isEqualTo(ABSENT);
    assertThat(tenantProperty(broker, "cluster.replication-factor")).isEqualTo(ABSENT);
    assertThat(tenantProperty(broker, "cluster.partition-count"))
        .as("the one field the tenant did override is still emitted")
        .isEqualTo(1);
  }

  @Test
  void shouldPickUpRootConfigurationAppliedAfterTheTenantWasDeclared() {
    // given - the tenant is declared before the root partition count is set, so a modifier applied
    // eagerly against the root-of-the-moment would see the wrong baseline
    final var broker =
        new TestStandaloneBroker()
            .withPtConfig(TENANT, camunda -> camunda.getCluster().setPartitionCount(1))
            .withClusterConfig(cluster -> cluster.setPartitionCount(2));

    // when
    broker.createSpringBuilder();

    // then
    assertThat(tenantProperty(broker, "cluster.partition-count"))
        .as("declaration order between root and tenant configuration does not matter")
        .isEqualTo(1);
  }

  @Test
  void shouldLetTenantModifierBuildOnTheRootValueItWouldInherit() {
    // given - a root snapshot period, and a tenant modifier that derives its value from whatever
    // the tenant would otherwise inherit. The pristine default is 5m, so a modifier reading a
    // pristine instance would land on 6m instead
    final var broker =
        new TestStandaloneBroker()
            .withDataConfig(data -> data.setSnapshotPeriod(Duration.ofMinutes(10)))
            .withPtConfig(
                TENANT,
                camunda ->
                    camunda
                        .getData()
                        .setSnapshotPeriod(camunda.getData().getSnapshotPeriod().plusMinutes(1)));

    // when
    broker.createSpringBuilder();

    // then
    assertThat(tenantProperty(broker, "data.snapshot-period"))
        .as("a tenant modifier observes the root's values, not pristine defaults")
        .hasToString(Duration.ofMinutes(11).toString());
  }

  @Test
  void shouldDeclareAnInitializationBlockCopiedFromTheRoot() {
    // given - the shape CamundaMultiDBExtension uses: the root seeds an initialization user, and
    // the tenant copies that block verbatim because PhysicalTenantRequiredOverrideValidation
    // rejects a tenant that has none of its own. Copying it changes nothing relative to the root,
    // so a root-only diff would drop it and the broker would fail to resolve the tenant at all
    final var rootUser = new ConfiguredUser("demo", "demo", "Demo", "demo@example.com");
    final var broker =
        new TestStandaloneBroker()
            .withSecurityConfig(
                security -> security.getInitialization().setUsers(List.of(rootUser)))
            .withPtConfig(
                TENANT,
                camunda -> camunda.getSecurity().getInitialization().setUsers(List.of(rootUser)));

    // when
    broker.createSpringBuilder();

    // then
    assertThat(tenantProperty(broker, "security.initialization.users[0].username"))
        .as("the tenant declares its own initialization block even when copied from the root")
        .isEqualTo("demo");
  }

  private static Object tenantProperty(final TestStandaloneBroker broker, final String relative) {
    return broker.property(
        "camunda.physical-tenants." + TENANT + "." + relative, Object.class, ABSENT);
  }
}
