/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.security.api.model.authz.AuthorizationOwnerType;
import io.camunda.security.api.model.authz.AuthorizationResourceMatcher;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.api.model.policy.PolicyAuthorization;
import io.camunda.security.api.model.policy.PolicyAuthorizationKey;
import io.camunda.security.api.model.policy.PolicyChangeSet;
import io.camunda.security.api.model.policy.PolicyChangeSetKind;
import io.camunda.security.api.model.policy.PolicyGroup;
import io.camunda.security.api.model.policy.PolicyRole;
import io.camunda.security.api.model.policy.PolicyTenant;
import io.camunda.security.api.model.policy.PolicyUpserts;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;

/**
 * Starts a broker, applies a policy change set through the {@link PolicyUpdateInPort}, and checks
 * that the change set was split into several chunks and that all entities were created.
 */
public final class PolicyUpdateInPortTest {

  private static final int ROLES = 50;
  private static final int GROUPS = 50;
  private static final int TENANTS = 10;
  private static final int AUTHORIZATIONS_PER_OWNER = 5;
  private static final int DESCRIPTION_CHARS = 8_000;
  private static final int EXPECTED_CHUNKS = 2;

  @Rule public final EmbeddedBrokerRule broker = new EmbeddedBrokerRule();

  @Test
  public void shouldSplitAndApplyLargeChangeSetThroughTheBroker() {
    // given
    final var inPort = broker.getBroker().getPolicyUpdateInPort();
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(inPort.isCoordinatorLeader()).isTrue());
    final var envelopeId = UUID.randomUUID().toString();

    // when
    final int chunks = inPort.apply(envelopeId, largeChangeSet());

    // then
    assertThat(chunks).isEqualTo(EXPECTED_CHUNKS);
    await()
        .atMost(Duration.ofMinutes(10))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        RecordingExporter.records()
                            .withValueType(ValueType.POLICY_UPDATE)
                            .withIntent(PolicyUpdateIntent.APPLIED)
                            .filter(
                                record ->
                                    ((PolicyUpdateRecordValue) record.getValue()).isLastPage())
                            .limit(1)
                            .count())
                    .isEqualTo(1));
    awaitExported(ValueType.ROLE, RoleIntent.CREATED, ROLES);
    awaitExported(ValueType.GROUP, GroupIntent.CREATED, GROUPS);
    awaitExported(ValueType.TENANT, TenantIntent.CREATED, TENANTS);
    awaitExported(
        ValueType.AUTHORIZATION,
        AuthorizationIntent.CREATED,
        (ROLES + GROUPS) * AUTHORIZATIONS_PER_OWNER);
  }

  /**
   * Waits until the expected number of records is exported. The per-query wait of the {@link
   * RecordingExporter} is short, so the query is retried until the count is reached.
   */
  private static void awaitExported(
      final ValueType valueType, final Intent intent, final int expected) {
    await()
        .atMost(Duration.ofMinutes(10))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        RecordingExporter.records()
                            .withValueType(valueType)
                            .withIntent(intent)
                            .limit(expected)
                            .count())
                    .isEqualTo(expected));
  }

  private static PolicyChangeSet largeChangeSet() {
    final var description = "d".repeat(DESCRIPTION_CHARS);
    final var roles = new ArrayList<PolicyRole>();
    final var groups = new ArrayList<PolicyGroup>();
    final var tenants = new ArrayList<PolicyTenant>();
    final var authorizations = new ArrayList<PolicyAuthorization>();
    for (int role = 0; role < ROLES; role++) {
      roles.add(new PolicyRole("role-" + role, "Role " + role, description));
      authorizations.addAll(authorizations(AuthorizationOwnerType.ROLE, "role-" + role));
    }
    for (int group = 0; group < GROUPS; group++) {
      groups.add(new PolicyGroup("group-" + group, "Group " + group, description));
      authorizations.addAll(authorizations(AuthorizationOwnerType.GROUP, "group-" + group));
    }
    for (int tenant = 0; tenant < TENANTS; tenant++) {
      tenants.add(new PolicyTenant("tenant-" + tenant, "Tenant " + tenant, description));
    }
    final var upserts = new PolicyUpserts(tenants, roles, groups, null, null, authorizations);
    return new PolicyChangeSet(1L, PolicyChangeSetKind.DELTA, upserts, null);
  }

  private static List<PolicyAuthorization> authorizations(
      final AuthorizationOwnerType ownerType, final String ownerId) {
    final var result = new ArrayList<PolicyAuthorization>();
    for (int resource = 0; resource < AUTHORIZATIONS_PER_OWNER; resource++) {
      result.add(
          new PolicyAuthorization(
              new PolicyAuthorizationKey(
                  ownerType,
                  ownerId,
                  AuthorizationResourceType.AUDIT_LOG,
                  AuthorizationResourceMatcher.ID,
                  "resource-" + resource,
                  ""),
              Set.of(PermissionType.READ)));
    }
    return result;
  }
}
