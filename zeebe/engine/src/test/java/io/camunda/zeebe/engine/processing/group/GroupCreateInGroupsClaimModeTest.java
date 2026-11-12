/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.group;

import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * In groups-claim mode, a group ID can be assigned to a role without a Camunda group, because the
 * groups come from the token. Creating a group with that ID keeps the assignment.
 */
public class GroupCreateInGroupsClaimModeTest {

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withSecurityConfig(
              cfg -> {
                cfg.getAuthentication().setMethod(AuthenticationMethod.OIDC);
                cfg.getAuthentication().getOidc().setGroupsClaim("groups");
              });

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldKeepTheRoleAssignmentOfAClaimedGroupWhenTheGroupIsCreated() {
    // given a role assigned to a group from the token, which has no Camunda group
    final var groupId = Strings.newRandomValidIdentityId();
    final var roleId = Strings.newRandomValidIdentityId();
    engine.role().newRole(roleId).create();
    engine.role().addEntity(roleId).withEntityId(groupId).withEntityType(EntityType.GROUP).add();

    // when
    engine.group().newGroup(groupId).withName("group").create();

    // then the assignment is kept, so assigning it again is rejected
    final var rejection =
        engine
            .role()
            .addEntity(roleId)
            .withEntityId(groupId)
            .withEntityType(EntityType.GROUP)
            .expectRejection()
            .add();
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.ALREADY_EXISTS);
  }
}
