/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.security.api.model.authz.AuthorizationOwnerType;
import io.camunda.security.api.model.authz.AuthorizationResourceMatcher;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.api.model.policy.PolicyAuthorization;
import io.camunda.security.api.model.policy.PolicyAuthorizationKey;
import io.camunda.security.api.model.policy.PolicyChangeSet;
import io.camunda.security.api.model.policy.PolicyChangeSetKind;
import io.camunda.security.api.model.policy.PolicyDeletions;
import io.camunda.security.api.model.policy.PolicyGroup;
import io.camunda.security.api.model.policy.PolicyMappingRule;
import io.camunda.security.api.model.policy.PolicyMembership;
import io.camunda.security.api.model.policy.PolicyMembershipTarget;
import io.camunda.security.api.model.policy.PolicyRole;
import io.camunda.security.api.model.policy.PolicyTenant;
import io.camunda.security.api.model.policy.PolicyUpserts;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PolicyChangeSetSplitterTest {

  private static final String ENVELOPE = "envelope-1";

  @Test
  void shouldProduceOneEmptyChunkForEmptyChangeSet() {
    // given
    final var changeSet = changeSet(2, new PolicyUpserts(null, null, null, null, null, null), null);
    final var splitter = new PolicyChangeSetSplitter(1024);

    // when
    final var chunks = splitter.split(ENVELOPE, changeSet);

    // then
    assertThat(chunks).hasSize(1);
    assertThat(chunks.getFirst().getChunkCount()).isEqualTo(1);
    assertThat(chunks.getFirst().getTenants()).isEmpty();
    assertThat(chunks.getFirst().getDeletes()).isEmpty();
  }

  @Test
  void shouldKeepEveryEntityWhenSplittingIntoSmallChunks() {
    // given
    final var changeSet = fullChangeSet(3);
    final var splitter = new PolicyChangeSetSplitter(200);

    // when
    final var chunks = splitter.split(ENVELOPE, changeSet);

    // then
    assertThat(chunks).hasSizeGreaterThan(1);
    assertThat(chunks.stream().mapToInt(c -> c.getRoles().size()).sum()).isEqualTo(3);
    assertThat(chunks.stream().mapToInt(c -> c.getTenants().size()).sum()).isEqualTo(3);
    assertThat(chunks.stream().mapToInt(c -> c.getMemberships().size()).sum()).isEqualTo(1);
    assertThat(chunks.stream().mapToInt(c -> c.getAuthorizations().size()).sum()).isEqualTo(1);
    assertThat(chunks.stream().mapToInt(c -> c.getDeletes().size()).sum()).isEqualTo(4);
    assertThat(chunks.stream().mapToInt(c -> c.getMembershipDeletes().size()).sum()).isEqualTo(1);
    assertThat(chunks.stream().mapToInt(c -> c.getAuthorizationDeletes().size()).sum())
        .isEqualTo(1);
  }

  @Test
  void shouldOrderChunksByApplyPhase() {
    // given: a budget that puts every entity in its own chunk
    final var changeSet = fullChangeSet(1);
    final var splitter = new PolicyChangeSetSplitter(1);

    // when
    final var chunks = splitter.split(ENVELOPE, changeSet);

    // then
    final int upsertIndex = indexOf(chunks, c -> !c.getRoles().isEmpty());
    final int membershipIndex = indexOf(chunks, c -> !c.getMemberships().isEmpty());
    final int authorizationIndex = indexOf(chunks, c -> !c.getAuthorizations().isEmpty());
    final int authorizationDeleteIndex =
        indexOf(chunks, c -> !c.getAuthorizationDeletes().isEmpty());
    final int membershipDeleteIndex = indexOf(chunks, c -> !c.getMembershipDeletes().isEmpty());
    final int entityDeleteIndex = indexOf(chunks, c -> !c.getDeletes().isEmpty());
    assertThat(upsertIndex)
        .isLessThan(membershipIndex)
        .isLessThan(authorizationIndex)
        .isLessThan(authorizationDeleteIndex)
        .isLessThan(membershipDeleteIndex)
        .isLessThan(entityDeleteIndex);
  }

  @Test
  void shouldPutOversizedEntityInItsOwnChunk() {
    // given
    final var hugeDescription = "x".repeat(10_000);
    final var upserts =
        new PolicyUpserts(
            null,
            List.of(
                new PolicyRole("small-1", "Small", "d"),
                new PolicyRole("huge", "Huge", hugeDescription),
                new PolicyRole("small-2", "Small", "d")),
            null,
            null,
            null,
            null);
    final var splitter = new PolicyChangeSetSplitter(500);

    // when
    final var chunks = splitter.split(ENVELOPE, changeSet(4, upserts, null));

    // then
    final var hugeChunk =
        chunks.stream()
            .filter(c -> c.getRoles().stream().anyMatch(r -> "huge".equals(r.getRoleId())))
            .findFirst()
            .orElseThrow();
    assertThat(hugeChunk.getRoles()).hasSize(1);
    assertThat(chunks.stream().mapToInt(c -> c.getRoles().size()).sum()).isEqualTo(3);
  }

  @Test
  void shouldSetChunkIndexCountKindAndVersionOnEveryChunk() {
    // given
    final var changeSet = fullChangeSet(2);
    final var splitter = new PolicyChangeSetSplitter(200);

    // when
    final var chunks = splitter.split(ENVELOPE, changeSet);

    // then
    for (int index = 0; index < chunks.size(); index++) {
      final PolicyUpdateRecord chunk = chunks.get(index);
      assertThat(chunk.getEnvelopeId()).isEqualTo(ENVELOPE);
      assertThat(chunk.getPolicyVersion()).isEqualTo(changeSet.policyVersion());
      assertThat(chunk.getChunkIndex()).isEqualTo(index);
      assertThat(chunk.getChunkCount()).isEqualTo(chunks.size());
    }
  }

  @Test
  void shouldMapEveryOwnerTypeByName() {
    // given: one authorization per owner type the security library defines
    final var ownerTypes = EnumSet.complementOf(EnumSet.of(AuthorizationOwnerType.UNSPECIFIED));
    final var authorizations =
        ownerTypes.stream()
            .map(
                ownerType ->
                    new PolicyAuthorization(
                        new PolicyAuthorizationKey(
                            ownerType,
                            "owner-" + ownerType.name(),
                            AuthorizationResourceType.AUDIT_LOG,
                            AuthorizationResourceMatcher.ANY,
                            "*",
                            ""),
                        Set.of(PermissionType.READ)))
            .toList();
    final var upserts = new PolicyUpserts(null, null, null, null, null, authorizations);
    final var splitter =
        new PolicyChangeSetSplitter(PolicyChangeSetSplitter.DEFAULT_CHUNK_BUDGET_BYTES);

    // when
    final var chunks = splitter.split(ENVELOPE, changeSet(2, upserts, null));

    // then
    final var mapped =
        chunks.stream()
            .flatMap(c -> c.getAuthorizations().stream())
            .map(a -> a.getOwnerType().name())
            .toList();
    assertThat(mapped)
        .containsExactlyInAnyOrderElementsOf(ownerTypes.stream().map(Enum::name).toList());
  }

  @Test
  void shouldRejectNonPositiveBudget() {
    assertThatThrownBy(() -> new PolicyChangeSetSplitter(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static <T> int indexOf(
      final List<T> chunks, final java.util.function.Predicate<T> predicate) {
    return java.util.stream.IntStream.range(0, chunks.size())
        .filter(i -> predicate.test(chunks.get(i)))
        .findFirst()
        .orElseThrow();
  }

  private static PolicyChangeSet fullChangeSet(final int entityCount) {
    final var tenants = new java.util.ArrayList<PolicyTenant>();
    final var roles = new java.util.ArrayList<PolicyRole>();
    for (int i = 0; i < entityCount; i++) {
      tenants.add(new PolicyTenant("tenant-" + i, "Tenant " + i, "description"));
      roles.add(new PolicyRole("role-" + i, "Role " + i, "description"));
    }
    final var groups = List.of(new PolicyGroup("group-1", "Group", "description"));
    final var mappingRules = List.of(new PolicyMappingRule("rule-1", "Rule", "claim", "value"));
    final var memberships =
        List.of(
            new PolicyMembership(PolicyMembershipTarget.ROLE, "role-0", EntityType.USER, "alice"));
    final var authorizations =
        List.of(
            new PolicyAuthorization(
                new PolicyAuthorizationKey(
                    AuthorizationOwnerType.ROLE,
                    "role-0",
                    AuthorizationResourceType.AUDIT_LOG,
                    AuthorizationResourceMatcher.ANY,
                    "*",
                    ""),
                Set.of(PermissionType.READ)));
    final var upserts =
        new PolicyUpserts(tenants, roles, groups, mappingRules, memberships, authorizations);
    final var deletions =
        new PolicyDeletions(
            List.of("tenant-old"),
            List.of("role-old"),
            List.of("group-old"),
            List.of("rule-old"),
            List.of(
                new PolicyMembership(
                    PolicyMembershipTarget.ROLE, "role-old", EntityType.USER, "bob")),
            List.of(
                new PolicyAuthorizationKey(
                    AuthorizationOwnerType.ROLE,
                    "role-old",
                    AuthorizationResourceType.AUDIT_LOG,
                    AuthorizationResourceMatcher.ANY,
                    "*",
                    "")));
    return changeSet(5, upserts, deletions);
  }

  private static PolicyChangeSet changeSet(
      final long version, final PolicyUpserts upserts, final PolicyDeletions deletions) {
    return new PolicyChangeSet(version, PolicyChangeSetKind.DELTA, upserts, deletions);
  }
}
