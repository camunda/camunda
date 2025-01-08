/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.group;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class SimpleMappingAssignmentTest {

  @Rule
  public final EngineRule engine = EngineRule.singlePartition().withoutAwaitingIdentitySetup();

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldBeAbleToAddUserToGroupUsingOIDC() {
    // given
    final var username = UUID.randomUUID().toString();
    final var groupName = UUID.randomUUID().toString();
    final var groupId = UUID.randomUUID().toString();
    engine.group().newGroup(groupName).withGroupId(groupId).create();

    // when
    // assign user to group
    final var addedEntityRecord =
        engine.group().addEntity(groupId).withEntityId(username).add().getValue();

    // then user should be assigned
    assertThat(addedEntityRecord).hasGroupId(groupId).hasEntityId(username);

    // Should be able to remove entity
    final var removedEntityRecord =
        engine.group().removeEntity(groupId).withEntityId(username).remove().getValue();
    assertThat(removedEntityRecord).hasGroupId(groupId).hasEntityId(username);
  }
}
