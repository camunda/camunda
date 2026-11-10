/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.identity.IdentityView;
import io.camunda.zeebe.engine.processing.policy.PolicyUpdateCommandMapper.IdentityCommand;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PolicyUpdateIdentityViewTest {

  private final IdentityView state = mock(IdentityView.class);
  private final PolicyUpdateIdentityView view = new PolicyUpdateIdentityView(state);

  @Test
  void shouldSeeEntitiesThatAnEarlierCommandCreates() {
    // given
    final var roleId = uid("role");
    final var groupId = uid("group");
    final var ruleId = uid("rule");

    // when
    view.apply(new IdentityCommand(RoleIntent.CREATE, new RoleRecord().setRoleId(roleId)));
    view.apply(new IdentityCommand(GroupIntent.CREATE, new GroupRecord().setGroupId(groupId)));
    view.apply(
        new IdentityCommand(
            MappingRuleIntent.CREATE, new MappingRuleRecord().setMappingRuleId(ruleId)));

    // then
    assertThat(view.roleExists(roleId)).isTrue();
    assertThat(view.groupExists(groupId)).isTrue();
    assertThat(view.mappingRuleExists(ruleId)).isTrue();
  }

  @Test
  void shouldNotSeeAnEntityThatAnEarlierCommandDeletes() {
    // given
    final var roleId = uid("role");
    when(state.roleExists(roleId)).thenReturn(true);

    // when
    view.apply(new IdentityCommand(RoleIntent.DELETE, new RoleRecord().setRoleId(roleId)));

    // then
    assertThat(view.roleExists(roleId)).isFalse();
  }

  @Test
  void shouldSeeMembershipsThatEarlierCommandsAddAndRemove() {
    // given
    final var roleId = uid("role");
    final var added = uid("client");
    final var removed = uid("client");
    when(state.hasRelation(EntityType.CLIENT, removed, RelationType.ROLE, roleId)).thenReturn(true);

    // when
    view.apply(new IdentityCommand(RoleIntent.ADD_ENTITY, member(roleId, added)));
    view.apply(new IdentityCommand(RoleIntent.REMOVE_ENTITY, member(roleId, removed)));

    // then
    assertThat(view.hasRelation(EntityType.CLIENT, added, RelationType.ROLE, roleId)).isTrue();
    assertThat(view.hasRelation(EntityType.CLIENT, removed, RelationType.ROLE, roleId)).isFalse();
  }

  @Test
  void shouldReadUnchangedEntitiesFromTheState() {
    // given
    final var roleId = uid("role");
    when(state.roleExists(roleId)).thenReturn(true);

    // when
    view.apply(new IdentityCommand(RoleIntent.CREATE, new RoleRecord().setRoleId(uid("role"))));

    // then
    assertThat(view.roleExists(roleId)).isTrue();
    assertThat(view.roleExists(uid("role"))).isFalse();
  }

  private static RoleRecord member(final String roleId, final String clientId) {
    return new RoleRecord()
        .setRoleId(roleId)
        .setEntityType(EntityType.CLIENT)
        .setEntityId(clientId);
  }

  private static String uid(final String prefix) {
    return prefix + "-" + UUID.randomUUID();
  }
}
