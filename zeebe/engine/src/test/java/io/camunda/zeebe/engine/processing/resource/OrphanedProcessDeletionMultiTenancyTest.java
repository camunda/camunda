/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static io.camunda.zeebe.engine.processing.resource.OrphanedProcessDefinitions.assertFullyDeleted;
import static io.camunda.zeebe.engine.processing.resource.OrphanedProcessDefinitions.orphanOnPartitions;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.initialization.ConfiguredTenant;
import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
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

public class OrphanedProcessDeletionMultiTenancyTest {

  private static final int PARTITION_COUNT = 3;
  private static final int STUCK_PARTITION = 2;
  private static final String USER_A = UUID.randomUUID().toString();
  private static final String USER_B = UUID.randomUUID().toString();
  private static final String TENANT_A = Strings.newRandomValidTenantId();
  private static final String TENANT_B = Strings.newRandomValidTenantId();

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withIdentitySetup()
          .withMultiTenancyChecksEnabled(true)
          .withSecurityConfig(
              cfg ->
                  cfg.getInitialization()
                      .setUsers(
                          List.of(
                              new ConfiguredUser(USER_A, "password", "name", "email"),
                              new ConfiguredUser(USER_B, "password", "name", "email"))))
          .withSecurityConfig(
              cfg -> {
                final var defaultRoles = new HashMap<>(cfg.getInitialization().getDefaultRoles());
                defaultRoles.put("admin", Map.of("users", List.of(USER_A, USER_B)));
                cfg.getInitialization().setDefaultRoles(defaultRoles);
              })
          .withSecurityConfig(
              cfg ->
                  cfg.getInitialization()
                      .setTenants(
                          List.of(
                              tenantWithUser(TENANT_A, USER_A), tenantWithUser(TENANT_B, USER_B))));

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldNotRecoverOrphanedDeletionOfAnotherTenant() {
    // given
    final var metadata = deployOnAllPartitions(TENANT_A);
    final long processDefinitionKey = metadata.getProcessDefinitionKey();
    orphanOnPartitions(engine, metadata, PARTITION_COUNT, STUCK_PARTITION);

    // when
    final var deletion =
        engine
            .resourceDeletion()
            .withResourceKey(processDefinitionKey)
            .withAuthorizedTenantIds(TENANT_B)
            .delete(USER_B);

    // then - the deployment partition no longer knows the definition's tenant and accepts the retry
    Assertions.assertThat(deletion).hasIntent(ResourceDeletionIntent.DELETED);

    // and - the stuck partition does not find it within the caller's tenants and keeps its copy
    Assertions.assertThat(
            RecordingExporter.resourceDeletionRecords(ResourceDeletionIntent.DELETE)
                .onlyCommandRejections()
                .withResourceKey(processDefinitionKey)
                .withPartitionId(STUCK_PARTITION)
                .getFirst())
        .hasRejectionType(RejectionType.NOT_FOUND);
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    records
                        .processRecords()
                        .withIntent(ProcessIntent.DRAINING)
                        .withProcessDefinitionKey(processDefinitionKey)
                        .withPartitionId(STUCK_PARTITION)
                        .exists()))
        .describedAs("another tenant must not start deleting the stuck partition's copy")
        .isFalse();

    // when - a caller of the owning tenant retries
    engine
        .resourceDeletion()
        .withResourceKey(processDefinitionKey)
        .withAuthorizedTenantIds(TENANT_A)
        .delete(USER_A);

    // then
    assertFullyDeleted(processDefinitionKey);
  }

  private static ConfiguredTenant tenantWithUser(final String tenantId, final String username) {
    return new ConfiguredTenant(
        tenantId, tenantId, "", List.of(username), List.of(), List.of(), List.of(), List.of());
  }

  private ProcessMetadataValue deployOnAllPartitions(final String tenantId) {
    final var processId = Strings.newRandomValidBpmnId();
    final var metadata =
        engine
            .deployment()
            .withXmlResource(
                "process.bpmn",
                Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
            .withTenantId(tenantId)
            .deploy()
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
}
