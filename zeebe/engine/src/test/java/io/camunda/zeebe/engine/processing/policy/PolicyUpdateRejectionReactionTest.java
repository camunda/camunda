/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.apply;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.chunk;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.uid;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateCommitVersionTest.versionCommitted;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.authz.DefaultRole;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyAuthorizationDeleteEntry;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyDeleteEntry;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyMembershipEntry;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.PolicyAuthorizationDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyMembershipEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateEntityType;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Pins, for each kind of rejection an identity processor can give a command of a policy update,
 * whether the rejection fails the update or only warns and lets it commit. Each identity processor
 * makes that choice at its rejection, so a row per kind keeps a changed choice from going
 * unnoticed.
 *
 * <p>A row prepares the identity state with an earlier, committed update, then applies an update
 * whose identity command is rejected.
 */
@RunWith(Parameterized.class)
public final class PolicyUpdateRejectionReactionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter(0)
  public String description;

  @Parameter(1)
  public Scenario scenario;

  @Parameters(name = "{0}")
  public static List<Object[]> scenarios() {
    return List.of(
        // the command asks for what is already the case, so the update goes on
        warns(
            "role DELETE of a missing role",
            Scenario.of(ids -> deletes(delete(PolicyUpdateEntityType.ROLE, ids.role())))
                .rejects(RoleIntent.DELETE, RejectionType.NOT_FOUND)),
        warns(
            "group DELETE of a missing group",
            Scenario.of(ids -> deletes(delete(PolicyUpdateEntityType.GROUP, ids.group())))
                .rejects(GroupIntent.DELETE, RejectionType.NOT_FOUND)),
        warns(
            "tenant DELETE of a missing tenant",
            Scenario.of(ids -> deletes(delete(PolicyUpdateEntityType.TENANT, ids.tenant())))
                .rejects(TenantIntent.DELETE, RejectionType.NOT_FOUND)),
        warns(
            "mapping rule DELETE of a missing mapping rule",
            Scenario.of(ids -> deletes(delete(PolicyUpdateEntityType.MAPPING_RULE, ids.rule())))
                .rejects(MappingRuleIntent.DELETE, RejectionType.NOT_FOUND)),
        warns(
            "authorization DELETE of a missing authorization",
            Scenario.of(
                    ids ->
                        chunk()
                            .setAuthorizationDeletes(
                                List.<PolicyAuthorizationDeleteEntryValue>of(
                                    authorizationDelete(ids.role(), ids.process()))))
                .rejects(AuthorizationIntent.DELETE, RejectionType.NOT_FOUND)),
        warns(
            "role REMOVE_ENTITY from a missing role",
            Scenario.of(ids -> removes(member(PolicyUpdateEntityType.ROLE, ids.role(), ids)))
                .rejects(RoleIntent.REMOVE_ENTITY, RejectionType.NOT_FOUND)),
        warns(
            "role REMOVE_ENTITY of a member that is not assigned",
            Scenario.given(PolicyUpdateRejectionReactionTest::targetsAndRule)
                .when(ids -> removes(member(PolicyUpdateEntityType.ROLE, ids.role(), ids)))
                .rejects(RoleIntent.REMOVE_ENTITY, RejectionType.NOT_FOUND)),
        warns(
            "group REMOVE_ENTITY of a member that is not assigned",
            Scenario.given(PolicyUpdateRejectionReactionTest::targetsAndRule)
                .when(ids -> removes(member(PolicyUpdateEntityType.GROUP, ids.group(), ids)))
                .rejects(GroupIntent.REMOVE_ENTITY, RejectionType.NOT_FOUND)),
        warns(
            "tenant REMOVE_ENTITY of a member that is not assigned",
            Scenario.given(PolicyUpdateRejectionReactionTest::targetsAndRule)
                .when(ids -> removes(member(PolicyUpdateEntityType.TENANT, ids.tenant(), ids)))
                .rejects(TenantIntent.REMOVE_ENTITY, RejectionType.NOT_FOUND)),
        warns(
            "role ADD_ENTITY of a member that is already assigned",
            Scenario.given(ids -> targetsAndRule(ids).setMemberships(allMemberships(ids)))
                .when(ids -> adds(member(PolicyUpdateEntityType.ROLE, ids.role(), ids)))
                .rejects(RoleIntent.ADD_ENTITY, RejectionType.ALREADY_EXISTS)),
        warns(
            "group ADD_ENTITY of a member that is already assigned",
            Scenario.given(ids -> targetsAndRule(ids).setMemberships(allMemberships(ids)))
                .when(ids -> adds(member(PolicyUpdateEntityType.GROUP, ids.group(), ids)))
                .rejects(GroupIntent.ADD_ENTITY, RejectionType.ALREADY_EXISTS)),
        warns(
            "tenant ADD_ENTITY of a member that is already assigned",
            Scenario.given(ids -> targetsAndRule(ids).setMemberships(allMemberships(ids)))
                .when(ids -> adds(member(PolicyUpdateEntityType.TENANT, ids.tenant(), ids)))
                .rejects(TenantIntent.ADD_ENTITY, RejectionType.ALREADY_EXISTS)),
        // the command cannot be applied, so the update fails
        fails(
            "role ADD_ENTITY to a missing role",
            Scenario.given(ids -> rules(rule(ids.rule(), ids.claim())))
                .when(ids -> adds(member(PolicyUpdateEntityType.ROLE, ids.role(), ids)))
                .rejects(RoleIntent.ADD_ENTITY, RejectionType.NOT_FOUND)),
        fails(
            "group ADD_ENTITY to a missing group",
            Scenario.given(ids -> rules(rule(ids.rule(), ids.claim())))
                .when(ids -> adds(member(PolicyUpdateEntityType.GROUP, ids.group(), ids)))
                .rejects(GroupIntent.ADD_ENTITY, RejectionType.NOT_FOUND)),
        fails(
            "tenant ADD_ENTITY to a missing tenant",
            Scenario.given(ids -> rules(rule(ids.rule(), ids.claim())))
                .when(ids -> adds(member(PolicyUpdateEntityType.TENANT, ids.tenant(), ids)))
                .rejects(TenantIntent.ADD_ENTITY, RejectionType.NOT_FOUND)),
        fails(
            "role ADD_ENTITY of a missing member",
            Scenario.given(
                    ids ->
                        chunk()
                            .setRoles(
                                List.<RoleRecordValue>of(
                                    new RoleRecord().setRoleId(ids.role()).setName(ids.role()))))
                .when(ids -> adds(member(PolicyUpdateEntityType.ROLE, ids.role(), ids)))
                .rejects(RoleIntent.ADD_ENTITY, RejectionType.NOT_FOUND)),
        fails(
            "role CREATE of a role that is created twice",
            Scenario.of(
                    ids -> {
                      final var role = new RoleRecord().setRoleId(ids.role()).setName(ids.role());
                      return chunk().setRoles(List.<RoleRecordValue>of(role, role));
                    })
                .rejects(RoleIntent.CREATE, RejectionType.ALREADY_EXISTS)),
        fails(
            "group CREATE of a group that is created twice",
            Scenario.of(
                    ids -> {
                      final var group =
                          new GroupRecord().setGroupId(ids.group()).setName(ids.group());
                      return chunk().setGroups(List.<GroupRecordValue>of(group, group));
                    })
                .rejects(GroupIntent.CREATE, RejectionType.ALREADY_EXISTS)),
        fails(
            "tenant CREATE of a tenant that is created twice",
            Scenario.of(
                    ids -> {
                      final var tenant =
                          new TenantRecord().setTenantId(ids.tenant()).setName(ids.tenant());
                      return chunk().setTenants(List.<TenantRecordValue>of(tenant, tenant));
                    })
                .rejects(TenantIntent.CREATE, RejectionType.ALREADY_EXISTS)),
        fails(
            "mapping rule CREATE of a mapping rule that is created twice",
            Scenario.of(
                    ids -> {
                      final var rule = rule(ids.rule(), ids.claim());
                      return rules(rule, rule);
                    })
                .rejects(MappingRuleIntent.CREATE, RejectionType.ALREADY_EXISTS)),
        fails(
            "mapping rule CREATE with the claim of another mapping rule",
            Scenario.given(ids -> rules(rule(ids.rule(), ids.claim())))
                .when(ids -> rules(rule(ids.otherRule(), ids.claim())))
                .rejects(MappingRuleIntent.CREATE, RejectionType.ALREADY_EXISTS)),
        fails(
            "mapping rule UPDATE to the claim of another mapping rule",
            Scenario.given(
                    ids ->
                        rules(
                            rule(ids.rule(), ids.claim()),
                            rule(ids.otherRule(), "other-" + ids.claim())))
                .when(ids -> rules(rule(ids.otherRule(), ids.claim())))
                .rejects(MappingRuleIntent.UPDATE, RejectionType.ALREADY_EXISTS)),
        fails(
            "role DELETE of a default role",
            Scenario.of(
                    ids -> deletes(delete(PolicyUpdateEntityType.ROLE, DefaultRole.ADMIN.getId())))
                .rejects(RoleIntent.DELETE, RejectionType.INVALID_STATE)),
        fails(
            "authorization DELETE of an authorization of a default role",
            Scenario.given(
                    ids ->
                        chunk()
                            .setRoles(
                                List.<RoleRecordValue>of(
                                    new RoleRecord()
                                        .setRoleId(DefaultRole.ADMIN.getId())
                                        .setName(DefaultRole.ADMIN.getId())))
                            .setAuthorizations(
                                List.<AuthorizationRecordValue>of(
                                    authorization(DefaultRole.ADMIN.getId(), ids.process()))))
                .when(
                    ids ->
                        chunk()
                            .setAuthorizationDeletes(
                                List.<PolicyAuthorizationDeleteEntryValue>of(
                                    authorizationDelete(DefaultRole.ADMIN.getId(), ids.process()))))
                .rejects(AuthorizationIntent.DELETE, RejectionType.INVALID_STATE)));
  }

  @Test
  public void shouldReactOnTheRejectionAsExpected() {
    // given
    final var ids = Ids.unique();
    if (scenario.given() != null) {
      final var earlier = scenario.given().apply(ids);
      apply(ENGINE, earlier);
      versionCommitted(earlier.getUpdateId());
    }
    final var update = scenario.update().apply(ids).setPolicyVersion(2);

    // when
    apply(ENGINE, update);

    // then
    final var rejection =
        RecordingExporter.records()
            .skipUntil(record -> isEvent(record, PolicyUpdateIntent.CHUNK_APPLIED, update))
            .onlyCommandRejections()
            .filter(record -> record.getIntent() == scenario.intent())
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(scenario.rejectionType());

    final var outcome =
        RecordingExporter.policyUpdateRecords()
            .onlyEvents()
            .filter(record -> record.getValue().getUpdateId().equals(update.getUpdateId()))
            .filter(
                record ->
                    record.getIntent() == PolicyUpdateIntent.VERSION_COMMITTED
                        || record.getIntent() == PolicyUpdateIntent.FAILED)
            .getFirst();
    if (scenario.failsUpdate()) {
      assertThat(outcome.getIntent()).isEqualTo(PolicyUpdateIntent.FAILED);
      assertThat(outcome.getValue().getErrorMessage())
          .contains(
              "%s.%s".formatted(rejection.getValueType(), scenario.intent()),
              scenario.rejectionType().name());
    } else {
      assertThat(outcome.getIntent()).isEqualTo(PolicyUpdateIntent.VERSION_COMMITTED);
    }
  }

  private static Object[] warns(final String description, final Scenario scenario) {
    return new Object[] {"warns on " + description, scenario.withFailsUpdate(false)};
  }

  private static Object[] fails(final String description, final Scenario scenario) {
    return new Object[] {"fails on " + description, scenario.withFailsUpdate(true)};
  }

  private static boolean isEvent(
      final Record<?> record, final PolicyUpdateIntent intent, final PolicyUpdateRecord update) {
    return record.getRecordType() == RecordType.EVENT
        && record.getIntent() == intent
        && ((PolicyUpdateRecordValue) record.getValue()).getUpdateId().equals(update.getUpdateId());
  }

  /** A role, a group and a tenant, and a mapping rule that can become their member. */
  private static PolicyUpdateRecord targetsAndRule(final Ids ids) {
    return chunk()
        .setTenants(
            List.<TenantRecordValue>of(
                new TenantRecord().setTenantId(ids.tenant()).setName(ids.tenant())))
        .setRoles(
            List.<RoleRecordValue>of(new RoleRecord().setRoleId(ids.role()).setName(ids.role())))
        .setGroups(
            List.<GroupRecordValue>of(
                new GroupRecord().setGroupId(ids.group()).setName(ids.group())))
        .setMappingRules(List.<MappingRuleRecordValue>of(rule(ids.rule(), ids.claim())));
  }

  private static List<PolicyMembershipEntryValue> allMemberships(final Ids ids) {
    return List.of(
        member(PolicyUpdateEntityType.ROLE, ids.role(), ids),
        member(PolicyUpdateEntityType.GROUP, ids.group(), ids),
        member(PolicyUpdateEntityType.TENANT, ids.tenant(), ids));
  }

  private static PolicyMembershipEntry member(
      final PolicyUpdateEntityType targetType, final String targetId, final Ids ids) {
    return new PolicyMembershipEntry()
        .setTargetType(targetType)
        .setTargetId(targetId)
        .setMemberType(EntityType.MAPPING_RULE)
        .setMemberId(ids.rule());
  }

  private static PolicyUpdateRecord adds(final PolicyMembershipEntry membership) {
    return chunk().setMemberships(List.<PolicyMembershipEntryValue>of(membership));
  }

  private static PolicyUpdateRecord removes(final PolicyMembershipEntry membership) {
    return chunk().setMembershipDeletes(List.<PolicyMembershipEntryValue>of(membership));
  }

  private static PolicyUpdateRecord deletes(final PolicyDeleteEntry delete) {
    return chunk().setDeletes(List.<PolicyDeleteEntryValue>of(delete));
  }

  private static PolicyDeleteEntry delete(final PolicyUpdateEntityType type, final String id) {
    return new PolicyDeleteEntry().setEntityType(type).setId(id);
  }

  private static PolicyUpdateRecord rules(final MappingRuleRecord... rules) {
    return chunk().setMappingRules(List.<MappingRuleRecordValue>of(rules));
  }

  private static MappingRuleRecord rule(final String id, final String claimValue) {
    return new MappingRuleRecord()
        .setMappingRuleId(id)
        .setName(id)
        .setClaimName("department")
        .setClaimValue(claimValue);
  }

  private static AuthorizationRecord authorization(final String roleId, final String processId) {
    return new AuthorizationRecord()
        .setOwnerType(AuthorizationOwnerType.ROLE)
        .setOwnerId(roleId)
        .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
        .setResourceMatcher(AuthorizationResourceMatcher.ID)
        .setResourceId(processId)
        .setPermissionTypes(Set.of(PermissionType.READ_PROCESS_DEFINITION));
  }

  private static PolicyAuthorizationDeleteEntry authorizationDelete(
      final String roleId, final String processId) {
    return new PolicyAuthorizationDeleteEntry()
        .setOwnerType(AuthorizationOwnerType.ROLE)
        .setOwnerId(roleId)
        .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
        .setResourceMatcher(AuthorizationResourceMatcher.ID)
        .setResourceId(processId);
  }

  /** Unique entity IDs for one run of a row, so that rows don't see each other's entities. */
  record Ids(
      String role,
      String group,
      String tenant,
      String rule,
      String otherRule,
      String claim,
      String process) {
    static Ids unique() {
      final var id = uid();
      return new Ids(
          "role-" + id,
          "group-" + id,
          "tenant-" + id,
          "rule-" + id,
          "other-rule-" + id,
          "claim-" + id,
          "process-" + id);
    }
  }

  /**
   * @param given the update that prepares the identity state, or null if none is needed
   * @param update the update with the command that is rejected
   */
  record Scenario(
      Function<Ids, PolicyUpdateRecord> given,
      Function<Ids, PolicyUpdateRecord> update,
      Intent intent,
      RejectionType rejectionType,
      boolean failsUpdate) {

    static Scenario of(final Function<Ids, PolicyUpdateRecord> update) {
      return new Scenario(null, update, null, null, false);
    }

    static Scenario given(final Function<Ids, PolicyUpdateRecord> given) {
      return new Scenario(given, null, null, null, false);
    }

    Scenario when(final Function<Ids, PolicyUpdateRecord> update) {
      return new Scenario(given, update, intent, rejectionType, failsUpdate);
    }

    Scenario rejects(final Intent intent, final RejectionType rejectionType) {
      return new Scenario(given, update, intent, rejectionType, failsUpdate);
    }

    Scenario withFailsUpdate(final boolean failsUpdate) {
      return new Scenario(given, update, intent, rejectionType, failsUpdate);
    }
  }
}
