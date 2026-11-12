/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.protocol.record.value.ProbeRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/** Who may collect and answer a probe: PROBE:ANSWER on its probe ID, and its tenant. */
public final class ProbeAnswerAuthorizationTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withAuthorizationsEnabled(true)
          .withMultiTenancyChecksEnabled(true);

  private static final String TENANT = "probe-answer-tenant";
  private static final String OTHER_TENANT = "other-probe-answer-tenant";
  private static final String CLIENT = UUID.randomUUID().toString();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String probeId;
  private String otherProbeId;

  @BeforeClass
  public static void createTenantsAndClient() {
    ENGINE.tenant().newTenant().withTenantId(TENANT).withName(TENANT).create();
    ENGINE.tenant().newTenant().withTenantId(OTHER_TENANT).withName(OTHER_TENANT).create();
    createUser(CLIENT, TENANT, OTHER_TENANT);
    grant(CLIENT, PermissionType.CREATE, "*");
  }

  @Before
  public void setUp() {
    probeId = "validate-configuration/io.camunda:test-" + UUID.randomUUID() + ":1";
    otherProbeId = "validate-configuration/io.camunda:other-" + UUID.randomUUID() + ":1";
  }

  @Test
  public void shouldCollectOnlyTheProbesOfTheProbeIdsThePollerMayAnswer() {
    // given
    final long probeKey = createProbe(probeId, TENANT);
    createProbe(otherProbeId, TENANT);
    final var worker = createUser(UUID.randomUUID().toString(), TENANT);
    grant(worker, PermissionType.ANSWER, probeId);

    // when
    final var activated =
        ENGINE.probe().activate(List.of(probeId, otherProbeId), "worker", 10, worker);

    // then
    assertThat(activated.getValue().getProbeKeys()).containsExactly(probeKey);
    assertThat(activated.getValue().getProbes())
        .extracting(ProbeRecordValue::getProbeId)
        .containsExactly(probeId);
  }

  @Test
  public void shouldCollectNothingWithoutProbeAnswer() {
    // given
    createProbe(probeId, TENANT);
    final var worker = createUser(UUID.randomUUID().toString(), TENANT);
    grant(worker, PermissionType.CREATE, probeId);

    // when
    final var activated = ENGINE.probe().activate(List.of(probeId), "worker", 10, worker);

    // then
    assertThat(activated.getIntent())
        .describedAs("not rejected (D52)")
        .isEqualTo(ProbeBatchIntent.ACTIVATED);
    assertThat(activated.getValue().getProbes()).isEmpty();
  }

  @Test
  public void shouldNotCollectTheProbesOfATenantThePollerIsNotAssignedTo() {
    // given
    final long probeKey = createProbe(probeId, TENANT);
    createProbe(probeId, OTHER_TENANT);
    final var worker = createUser(UUID.randomUUID().toString(), TENANT);
    grant(worker, PermissionType.ANSWER, "*");

    // when
    final var activated = ENGINE.probe().activate(List.of(probeId), "worker", 10, worker);

    // then
    assertThat(activated.getValue().getProbeKeys()).containsExactly(probeKey);
  }

  @Test
  public void shouldLetAnyHolderOfProbeAnswerAnswerWithTheWorkerOfThePoll() {
    // given
    final long probeKey = activatedProbe(TENANT, "collecting-worker");
    final var otherWorker = createUser(UUID.randomUUID().toString(), TENANT);
    grant(otherWorker, PermissionType.ANSWER, probeId);

    // when
    final var answered = ENGINE.probe().answer(probeKey, unsupported(), otherWorker);

    // then
    assertThat(answered.getIntent()).isEqualTo(ProbeIntent.ANSWERED);
    assertThat(answered.getValue().getWorker()).isEqualTo("collecting-worker");
  }

  @Test
  public void shouldRejectAnAnswerWithoutProbeAnswer() {
    // given
    final long probeKey = activatedProbe(TENANT, "worker");
    final var otherWorker = createUser(UUID.randomUUID().toString(), TENANT);
    grant(otherWorker, PermissionType.ANSWER, otherProbeId);

    // when
    final var rejection =
        ENGINE.probe().expectRejection().answer(probeKey, unsupported(), otherWorker);

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.FORBIDDEN);
    assertThat(rejection.getRejectionReason())
        .startsWith("Insufficient permissions to perform operation 'ANSWER' on resource 'PROBE'");
  }

  @Test
  public void shouldRejectAnAnswerForAnotherTenantAsNotFound() {
    // given
    final long probeKey = activatedProbe(OTHER_TENANT, "worker");
    final var otherWorker = createUser(UUID.randomUUID().toString(), TENANT);
    grant(otherWorker, PermissionType.ANSWER, "*");

    // when
    final var rejection =
        ENGINE.probe().expectRejection().answer(probeKey, unsupported(), otherWorker);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to answer probe with key '%d', but no such probe was found."
                .formatted(probeKey));
  }

  @Test
  public void shouldCheckThePermissionBeforeTheEnvelope() {
    // given
    final long probeKey = activatedProbe(TENANT, "worker");
    final var otherWorker = createUser(UUID.randomUUID().toString(), TENANT);

    // when
    final var rejection =
        ENGINE.probe().expectRejection().answer(probeKey, new ProbeRecord(), otherWorker);

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.FORBIDDEN);
  }

  private long activatedProbe(final String tenantId, final String workerName) {
    final long probeKey = createProbe(probeId, tenantId);
    final var worker = createUser(UUID.randomUUID().toString(), tenantId);
    grant(worker, PermissionType.ANSWER, probeId);
    final var activated = ENGINE.probe().activate(List.of(probeId), workerName, 1, worker);
    assertThat(activated.getValue().getProbeKeys()).containsExactly(probeKey);
    return probeKey;
  }

  private long createProbe(final String probeId, final String tenantId) {
    return ENGINE
        .probe()
        .withProbeId(probeId)
        .withTenantId(tenantId)
        .withInput("={}")
        .create(CLIENT)
        .getKey();
  }

  private static ProbeRecord unsupported() {
    return new ProbeRecord().setOutcome(ProbeOutcome.UNSUPPORTED);
  }

  private static String createUser(final String username, final String... tenantIds) {
    ENGINE
        .user()
        .newUser(username)
        .withPassword(UUID.randomUUID().toString())
        .withName(username)
        .withEmail(username + "@example.com")
        .create();
    for (final String tenantId : tenantIds) {
      ENGINE
          .tenant()
          .addEntity(tenantId)
          .withEntityType(EntityType.USER)
          .withEntityId(username)
          .add();
    }
    return username;
  }

  private static void grant(
      final String username, final PermissionType permissionType, final String resourceId) {
    ENGINE
        .authorization()
        .newAuthorization()
        .withPermissions(permissionType)
        .withOwnerId(username)
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(AuthorizationResourceType.PROBE)
        .withResourceMatcher(
            "*".equals(resourceId)
                ? AuthorizationResourceMatcher.ANY
                : AuthorizationResourceMatcher.ID)
        .withResourceId(resourceId)
        .create();
  }
}
