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
import io.camunda.zeebe.engine.util.client.ProbeClient;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.ClusterVariableKind;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * The checks of a probe's creation, in their order: PROBE:CREATE and the tenant, the input parses
 * without an imprecise reference, SECRET:READ on each referenced secret, the input evaluates to a
 * context. A caller without PROBE:CREATE never makes the engine parse or evaluate the input.
 */
public final class ProbeCreateAuthorizationTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withAuthorizationsEnabled(true)
          .withMultiTenancyChecksEnabled(true);

  private static final String TENANT = "probe-tenant";
  private static final String OTHER_TENANT = "other-probe-tenant";
  private static final String ADMIN = UUID.randomUUID().toString();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String probeId;
  private String username;

  @BeforeClass
  public static void createTenants() {
    ENGINE.tenant().newTenant().withTenantId(TENANT).withName(TENANT).create();
    ENGINE.tenant().newTenant().withTenantId(OTHER_TENANT).withName(OTHER_TENANT).create();
    ENGINE.user().newUser(ADMIN).create();
    ENGINE
        .authorization()
        .newAuthorization()
        .withPermissions(PermissionType.CREATE)
        .withOwnerId(ADMIN)
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(AuthorizationResourceType.CLUSTER_VARIABLE)
        .withResourceMatcher(AuthorizationResourceMatcher.ANY)
        .withResourceId("*")
        .create();
  }

  @Before
  public void setUp() {
    probeId = "validate-configuration/io.camunda:test-" + UUID.randomUUID() + ":1";
    username = UUID.randomUUID().toString();
    ENGINE
        .user()
        .newUser(username)
        .withPassword(UUID.randomUUID().toString())
        .withName(username)
        .withEmail(username + "@example.com")
        .create();
    assignToTenant(TENANT);
  }

  @Test
  public void shouldCreateWithProbeCreateOnTheProbeId() {
    // given
    grant(AuthorizationResourceType.PROBE, PermissionType.CREATE, probeId);

    // when
    final var created = probe().withInput("={region: \"eu\"}").create(username);

    // then
    assertThat(created.getIntent()).isEqualTo(ProbeIntent.CREATED);
  }

  @Test
  public void shouldRejectWithoutProbeCreateBeforeParsingTheInput() {
    // when the input is not even valid FEEL
    final var rejection = probe().withInput("={region: ").expectRejection().create(username);

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.FORBIDDEN);
    assertThat(rejection.getRejectionReason())
        .startsWith("Insufficient permissions to perform operation 'CREATE' on resource 'PROBE'");
  }

  @Test
  public void shouldRejectWithProbeCreateOnAnotherProbeId() {
    // given
    grant(AuthorizationResourceType.PROBE, PermissionType.CREATE, probeId + "-other");

    // when
    final var rejection = probe().withInput("={}").expectRejection().create(username);

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.FORBIDDEN);
  }

  @Test
  public void shouldRejectForATenantTheUserIsNotAssignedTo() {
    // given
    grant(AuthorizationResourceType.PROBE, PermissionType.CREATE, probeId);

    // when
    final var rejection =
        probe().withTenantId(OTHER_TENANT).withInput("={}").expectRejection().create(username);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Expected to create a probe for tenant '%s', but the user is not assigned to this tenant."
                .formatted(OTHER_TENANT));
  }

  @Test
  public void shouldRejectWithoutSecretReadOnAReferencedSecret() {
    // given
    grant(AuthorizationResourceType.PROBE, PermissionType.CREATE, probeId);
    grant(AuthorizationResourceType.SECRET, PermissionType.READ, "region");

    // when
    final var rejection =
        probe()
            .withInput("={region: camunda.secrets.region, key: camunda.secrets.awsKey}")
            .expectRejection()
            .create(username);

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.FORBIDDEN);
    assertThat(rejection.getRejectionReason())
        .startsWith("Insufficient permissions to perform operation 'READ' on resource 'SECRET'")
        .contains("awsKey");
  }

  @Test
  public void shouldRejectWithoutSecretReadOnASecretReachedThroughAClusterVariable() {
    // given
    final var name = createClusterVariable(ClusterVariableKind.SECRET_REFERENCE, "cvSecret");
    grant(AuthorizationResourceType.PROBE, PermissionType.CREATE, probeId);

    // when
    final var rejection =
        probe()
            .withInput("={auth: camunda.vars.cluster.%s}".formatted(name))
            .expectRejection()
            .create(username);

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.FORBIDDEN);
    assertThat(rejection.getRejectionReason()).contains("SECRET").contains("cvSecret");
  }

  @Test
  public void shouldCreateWithSecretReadOnEachReferencedSecret() {
    // given
    final var name = createClusterVariable(ClusterVariableKind.SECRET_REFERENCE, "cvToken");
    grant(AuthorizationResourceType.PROBE, PermissionType.CREATE, probeId);
    grant(AuthorizationResourceType.SECRET, PermissionType.READ, "awsKey");
    grant(AuthorizationResourceType.SECRET, PermissionType.READ, "cvToken");

    // when
    final var created =
        probe()
            .withInput(
                "={key: camunda.secrets.awsKey, auth: camunda.vars.cluster.%s}".formatted(name))
            .create(username);

    // then
    assertThat(created.getIntent()).isEqualTo(ProbeIntent.CREATED);
  }

  @Test
  public void shouldNotRequireClusterVariableReadToUseAClusterVariable() {
    // given
    final var name = createClusterVariable(ClusterVariableKind.JSON, "notASecret");
    grant(AuthorizationResourceType.PROBE, PermissionType.CREATE, probeId);

    // when
    final var created =
        probe().withInput("={auth: camunda.vars.cluster.%s}".formatted(name)).create(username);

    // then
    assertThat(created.getValue().getInput())
        .isEqualTo(Map.of("auth", Map.of("token", "camunda.secrets.notASecret")));
  }

  @Test
  public void shouldRejectAnImpreciseReferenceBeforeCheckingSecretRead() {
    // given
    grant(AuthorizationResourceType.PROBE, PermissionType.CREATE, probeId);

    // when
    final var rejection =
        probe()
            .withInput("={tokens: [camunda.secrets.apiToken]}")
            .expectRejection()
            .create(username);

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldCheckSecretReadBeforeEvaluatingTheInput() {
    // given
    grant(AuthorizationResourceType.PROBE, PermissionType.CREATE, probeId);

    // when the input would also fail to evaluate
    final var rejection =
        probe()
            .withInput("={key: camunda.secrets.apiToken, value: assert(1, 1 > 2)}")
            .expectRejection()
            .create(username);

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.FORBIDDEN);
  }

  private ProbeClient probe() {
    return ENGINE.probe().withProbeId(probeId).withTenantId(TENANT);
  }

  private String createClusterVariable(final ClusterVariableKind kind, final String secretName) {
    final var name = "cv_" + UUID.randomUUID().toString().replace("-", "");
    ENGINE
        .clusterVariables()
        .withName(name)
        .setGlobalScope()
        .withKind(kind)
        .withValue(Map.of("token", "camunda.secrets." + secretName))
        .create(ADMIN);
    return name;
  }

  private void grant(
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final String resourceId) {
    ENGINE
        .authorization()
        .newAuthorization()
        .withPermissions(permissionType)
        .withOwnerId(username)
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(resourceType)
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId(resourceId)
        .create();
  }

  private void assignToTenant(final String tenantId) {
    ENGINE
        .tenant()
        .addEntity(tenantId)
        .withEntityType(EntityType.USER)
        .withEntityId(username)
        .add();
  }
}
