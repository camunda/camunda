/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy.hub;

import static io.camunda.zeebe.engine.processing.policy.hub.PolicyChangeSets.changeSet;
import static io.camunda.zeebe.engine.processing.policy.hub.PolicyChangeSets.delta;
import static io.camunda.zeebe.engine.processing.policy.hub.PolicyChangeSets.noDeletions;
import static io.camunda.zeebe.engine.processing.policy.hub.PolicyChangeSets.noUpserts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.fanout.protocol.model.PolicyAuthorization;
import io.camunda.fanout.protocol.model.PolicyAuthorizationKey;
import io.camunda.fanout.protocol.model.PolicyChangeSetKind;
import io.camunda.fanout.protocol.model.PolicyDeletions;
import io.camunda.fanout.protocol.model.PolicyGroup;
import io.camunda.fanout.protocol.model.PolicyMappingRule;
import io.camunda.fanout.protocol.model.PolicyMembership;
import io.camunda.fanout.protocol.model.PolicyRole;
import io.camunda.fanout.protocol.model.PolicyTenant;
import io.camunda.fanout.protocol.model.PolicyUpserts;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.PolicyAuthorizationDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyMembershipEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateEntityType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PolicyChangeSetMapperTest {

  private static final String UPDATE_ID = "update-1";

  @Test
  void shouldMapTheIdentifiersOfTheChangeSet() {
    // given
    final var changeSet = delta(noUpserts(), noDeletions());

    // when
    final var record = PolicyChangeSetMapper.map(changeSet, 42L, "update-42").get();

    // then
    assertThat(record.getPolicyId()).isEqualTo(changeSet.policyId());
    assertThat(record.getBasePolicyVersion()).isEqualTo(3L);
    assertThat(record.getPolicyVersion()).isEqualTo(4L);
    assertThat(record.getSeqNo()).isEqualTo(42L);
    assertThat(record.getUpdateId()).isEqualTo("update-42");
  }

  @Test
  void shouldMapTheEntityUpserts() {
    // given
    final var upserts =
        new PolicyUpserts(
            List.of(new PolicyTenant("tenant-1", "Tenant", "The tenant")),
            List.of(new PolicyRole("role-1", "Role", null)),
            List.of(new PolicyGroup("group-1", "Group", "The group")),
            List.of(new PolicyMappingRule("rule-1", "Rule", "department", "sales")),
            List.of(),
            List.of());

    // when
    final var record =
        PolicyChangeSetMapper.map(delta(upserts, noDeletions()), 1L, UPDATE_ID).get();

    // then
    assertThat(record.getTenants())
        .extracting("tenantId", "name", "description")
        .containsExactly(tuple("tenant-1", "Tenant", "The tenant"));
    assertThat(record.getRoles())
        .extracting("roleId", "name", "description")
        .containsExactly(tuple("role-1", "Role", ""));
    assertThat(record.getGroups())
        .extracting("groupId", "name", "description")
        .containsExactly(tuple("group-1", "Group", "The group"));
    assertThat(record.getMappingRules())
        .extracting("mappingRuleId", "name", "claimName", "claimValue")
        .containsExactly(tuple("rule-1", "Rule", "department", "sales"));
  }

  @Test
  void shouldMapMembershipsAndAuthorizations() {
    // given
    final var upserts =
        new PolicyUpserts(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(new PolicyMembership("ROLE", "role-1", "USER", "alice")),
            List.of(
                new PolicyAuthorization(
                    new PolicyAuthorizationKey(
                        "ROLE", "role-1", "PROCESS_DEFINITION", "ID", "process-1", null),
                    List.of("READ_PROCESS_DEFINITION", "CREATE_PROCESS_INSTANCE"))));

    // when
    final var record =
        PolicyChangeSetMapper.map(delta(upserts, noDeletions()), 1L, UPDATE_ID).get();

    // then
    assertThat(record.getMemberships())
        .extracting(
            PolicyMembershipEntryValue::getTargetType,
            PolicyMembershipEntryValue::getTargetId,
            PolicyMembershipEntryValue::getMemberType,
            PolicyMembershipEntryValue::getMemberId)
        .containsExactly(tuple(PolicyUpdateEntityType.ROLE, "role-1", EntityType.USER, "alice"));
    assertThat(record.getAuthorizations())
        .extracting(
            AuthorizationRecordValue::getOwnerType,
            AuthorizationRecordValue::getOwnerId,
            AuthorizationRecordValue::getResourceType,
            AuthorizationRecordValue::getResourceMatcher,
            AuthorizationRecordValue::getResourceId,
            AuthorizationRecordValue::getPermissionTypes)
        .containsExactly(
            tuple(
                AuthorizationOwnerType.ROLE,
                "role-1",
                AuthorizationResourceType.PROCESS_DEFINITION,
                AuthorizationResourceMatcher.ID,
                "process-1",
                Set.of(
                    PermissionType.READ_PROCESS_DEFINITION,
                    PermissionType.CREATE_PROCESS_INSTANCE)));
  }

  @Test
  void shouldMapTheDeletions() {
    // given
    final var deletions =
        new PolicyDeletions(
            List.of("tenant-1"),
            List.of("role-1"),
            List.of("group-1"),
            List.of("rule-1"),
            List.of(new PolicyMembership("GROUP", "group-2", "USER", "bob")),
            List.of(
                new PolicyAuthorizationKey(
                    "USER", "bob", "USER_TASK", "PROPERTY", null, "assignee")));

    // when
    final var record =
        PolicyChangeSetMapper.map(delta(noUpserts(), deletions), 1L, UPDATE_ID).get();

    // then the entity deletes follow the apply order: mapping rules, groups, roles, tenants
    assertThat(record.getDeletes())
        .extracting(PolicyDeleteEntryValue::getEntityType, PolicyDeleteEntryValue::getId)
        .containsExactly(
            tuple(PolicyUpdateEntityType.MAPPING_RULE, "rule-1"),
            tuple(PolicyUpdateEntityType.GROUP, "group-1"),
            tuple(PolicyUpdateEntityType.ROLE, "role-1"),
            tuple(PolicyUpdateEntityType.TENANT, "tenant-1"));
    assertThat(record.getMembershipDeletes())
        .extracting(
            PolicyMembershipEntryValue::getTargetType, PolicyMembershipEntryValue::getMemberId)
        .containsExactly(tuple(PolicyUpdateEntityType.GROUP, "bob"));
    assertThat(record.getAuthorizationDeletes())
        .extracting(
            PolicyAuthorizationDeleteEntryValue::getOwnerId,
            PolicyAuthorizationDeleteEntryValue::getResourceMatcher,
            PolicyAuthorizationDeleteEntryValue::getResourcePropertyName)
        .containsExactly(tuple("bob", AuthorizationResourceMatcher.PROPERTY, "assignee"));
  }

  @Test
  void shouldRejectASnapshot() {
    // given
    final var changeSet = changeSet(PolicyChangeSetKind.SNAPSHOT, noUpserts(), noDeletions());

    // when
    final var result = PolicyChangeSetMapper.map(changeSet, 1L, UPDATE_ID);

    // then
    assertThat(result.getLeft()).contains("SNAPSHOT").contains(changeSet.policyId());
  }

  @Test
  void shouldRejectAnUnknownType() {
    // given
    final var upserts =
        new PolicyUpserts(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(new PolicyMembership("ROLE", "role-1", "ROBOT", "r2d2")),
            List.of());

    // when
    final var result = PolicyChangeSetMapper.map(delta(upserts, noDeletions()), 1L, UPDATE_ID);

    // then
    assertThat(result.getLeft()).contains("membership member type 'ROBOT'").contains("USER");
  }

  @Test
  void shouldRejectAnUnspecifiedType() {
    // given
    final var deletions =
        new PolicyDeletions(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(new PolicyMembership("UNSPECIFIED", "role-1", "USER", "alice")),
            List.of());

    // when
    final var result = PolicyChangeSetMapper.map(delta(noUpserts(), deletions), 1L, UPDATE_ID);

    // then
    assertThat(result.getLeft())
        .contains("membership target type 'UNSPECIFIED'")
        .doesNotContain("none of UNSPECIFIED");
  }
}
