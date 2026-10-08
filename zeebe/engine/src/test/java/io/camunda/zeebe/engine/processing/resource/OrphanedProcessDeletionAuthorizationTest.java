/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static io.camunda.zeebe.engine.processing.resource.OrphanedProcessDefinitions.orphanOnPartition;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class OrphanedProcessDeletionAuthorizationTest {

  private static final int PARTITION_COUNT = 3;
  private static final int STUCK_PARTITION = 2;

  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withIdentitySetup()
          .withAuthorizationsEnabled(true)
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)))
          .withSecurityConfig(
              cfg -> {
                final var defaultRoles = new HashMap<>(cfg.getInitialization().getDefaultRoles());
                defaultRoles.put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername())));
                cfg.getInitialization().setDefaultRoles(defaultRoles);
              });

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRecoverOrphanedDeletionRetriedWithoutDeletePermission() {
    // given - a user who may not delete the process
    final var processId = Strings.newRandomValidBpmnId();
    final var metadata = deployOnAllPartitions(processId);
    final long processDefinitionKey = metadata.getProcessDefinitionKey();
    orphanOnPartition(engine, metadata, PARTITION_COUNT, STUCK_PARTITION);
    final var user = createUserOnAllPartitions();

    // when
    final var deletion =
        engine.resourceDeletion().withResourceKey(processDefinitionKey).delete(user.getUsername());

    // then - the retry only completes a deletion that was authorized when it was first requested
    Assertions.assertThat(deletion).hasIntent(ResourceDeletionIntent.DELETED);
    assertThat(
            RecordingExporter.processRecords()
                .withIntent(ProcessIntent.FULLY_DELETED)
                .withProcessDefinitionKey(processDefinitionKey)
                .withPartitionId(Protocol.DEPLOYMENT_PARTITION)
                .exists())
        .isTrue();
  }

  private ProcessMetadataValue deployOnAllPartitions(final String processId) {
    final var metadata =
        engine
            .deployment()
            .withXmlResource(
                "process.bpmn",
                Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
            .deploy(DEFAULT_USER.getUsername())
            .getValue()
            .getProcessesMetadata()
            .getFirst();
    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      RecordingExporter.processRecords()
          .withIntent(ProcessIntent.CREATED)
          .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
          .withPartitionId(partitionId)
          .await();
    }
    return metadata;
  }

  private UserRecordValue createUserOnAllPartitions() {
    final var user =
        engine
            .user()
            .newUser(UUID.randomUUID().toString())
            .withPassword(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .withEmail(UUID.randomUUID().toString())
            .create(DEFAULT_USER.getUsername())
            .getValue();
    RecordingExporter.userRecords(UserIntent.CREATED)
        .withUsername(user.getUsername())
        .withPartitionId(STUCK_PARTITION)
        .await();
    return user;
  }
}
