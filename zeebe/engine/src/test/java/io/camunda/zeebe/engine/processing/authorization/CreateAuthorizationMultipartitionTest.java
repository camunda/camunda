/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class CreateAuthorizationMultipartitionTest {

  private static final int PARTITION_COUNT = 3;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldTestLifecycle() {
    // when
    final var authorizationKey =
        engine
            .authorization()
            .newAuthorization()
            .withOwnerId("ownerId")
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceMatcher(AuthorizationResourceMatcher.ID)
            .withResourceId("resourceId")
            .withResourceType(AuthorizationResourceType.RESOURCE)
            .withPermissions(PermissionType.CREATE)
            .create()
            .getValue()
            .getAuthorizationKey();

    // then
    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limit(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED))
                .filter(
                    record ->
                        record.getValueType() == ValueType.AUTHORIZATION
                            || (record.getValueType() == ValueType.COMMAND_DISTRIBUTION
                                && ((CommandDistributionRecordValue) record.getValue()).getIntent()
                                    == AuthorizationIntent.CREATE)))
        .extracting(
            io.camunda.zeebe.protocol.record.Record::getIntent,
            io.camunda.zeebe.protocol.record.Record::getRecordType,
            r ->
                // We want to verify the partition id where the deletion was distributing to and
                // where it was completed. Since only the CommandDistribution records have a
                // value that contains the partition id, we use the partition id the record was
                // written on for the other records.
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .containsSubsequence(
            tuple(AuthorizationIntent.CREATE, RecordType.COMMAND, 1),
            tuple(AuthorizationIntent.CREATED, RecordType.EVENT, 1),
            tuple(CommandDistributionIntent.STARTED, RecordType.EVENT, 1))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 2))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 3))
        .endsWith(tuple(CommandDistributionIntent.FINISHED, RecordType.EVENT, 1));

    for (int partitionId = 2; partitionId < PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.authorizationRecords()
                  .withAuthorizationKey(authorizationKey)
                  .withPartitionId(partitionId)
                  .limit(r -> r.getIntent().equals(AuthorizationIntent.CREATED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .endsWith(AuthorizationIntent.CREATE, AuthorizationIntent.CREATED);
    }
  }

  @Test
  public void shouldDistributeInIdentityQueue() {
    // when
    engine
        .authorization()
        .newAuthorization()
        .withOwnerId("ownerId")
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId("resourceId")
        .withResourceType(AuthorizationResourceType.RESOURCE)
        .withPermissions(PermissionType.CREATE)
        .create();

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords()
                .limit(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED))
                .withIntent(CommandDistributionIntent.ENQUEUED))
        .extracting(r -> r.getValue().getQueueId())
        .containsOnly(DistributionQueue.IDENTITY.getQueueId());
  }

  @Test
  public void distributionShouldNotOvertakeOtherCommandsInSameQueue() {
    // given the user creation distribution is intercepted
    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      engine.interceptInterPartitionIntent(partitionId, UserIntent.CREATE);
    }
    engine
        .user()
        .newUser("foo")
        .withEmail("foo@bar")
        .withName("Foo Bar")
        .withPassword("zabraboof")
        .create()
        .getKey();

    // when
    engine
        .authorization()
        .newAuthorization()
        .withOwnerId("ownerId")
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId("resourceId")
        .withResourceType(AuthorizationResourceType.RESOURCE)
        .withPermissions(PermissionType.CREATE)
        .create();

    // Increase time to trigger a redistribution
    engine.increaseTime(Duration.ofMinutes(1));

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
                .limit(2))
        .extracting(r -> r.getValue().getValueType(), r -> r.getValue().getIntent())
        .containsExactly(
            tuple(ValueType.USER, UserIntent.CREATE),
            tuple(ValueType.AUTHORIZATION, AuthorizationIntent.CREATE));
  }

  @Test
  public void shouldRejectAuthorizationWithNonexistentMappingInMultiPartitionSetup() {
    // given
    final var nonexistentMappingId = "nonexistent-mapping-id";

    // when
    final var rejection =
        engine
            .authorization()
            .newAuthorization()
            .withOwnerId(nonexistentMappingId)
            .withOwnerType(AuthorizationOwnerType.MAPPING_RULE)
            .withResourceMatcher(AuthorizationResourceMatcher.ID)
            .withResourceId("resourceId")
            .withResourceType(AuthorizationResourceType.RESOURCE)
            .withPermissions(PermissionType.CREATE)
            .expectRejection()
            .create();

    // then
    assertThat(rejection.getRejectionType())
        .isEqualTo(io.camunda.zeebe.protocol.record.RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to create or update authorization with ownerId '%s', but a mapping rule with this ID does not exist."
                .formatted(nonexistentMappingId));
  }
}
