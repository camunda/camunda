/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class BatchEventsClaimsTest {

  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withIdentitySetup()
          .withSecurityConfig(
              cfg -> {
                cfg.getAuthorizations().setEnabled(true);
                cfg.getInitialization().setUsers(List.of(DEFAULT_USER));
                cfg.getInitialization()
                    .getDefaultRoles()
                    .put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername())));
              });

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldIncludeClaimsInBatchOperationCreatedEvents() {
    // when
    final var batchOperation =
        engine
            .batchOperation()
            .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .withFilter(new UnsafeBuffer("{\"hasIncident\": false}".getBytes()))
            .create(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.batchOperationCreationRecords(BatchOperationIntent.CREATED)
            .withBatchOperationKey(batchOperation.getKey())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInBatchOperationCanceledEvents() {
    // given
    final var batchOperation =
        engine
            .batchOperation()
            .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .withFilter(new UnsafeBuffer("{\"hasIncident\": false}".getBytes()))
            .create(DEFAULT_USER.getUsername());

    // when
    engine
        .batchOperation()
        .newLifecycle()
        .withBatchOperationKey(batchOperation.getKey())
        .cancel(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.batchOperationLifecycleRecords(BatchOperationIntent.CANCELED)
            .withBatchOperationKey(batchOperation.getKey())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInBatchOperationSuspendedEvents() {
    // given
    final var batchOperation =
        engine
            .batchOperation()
            .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .withFilter(new UnsafeBuffer("{\"hasIncident\": false}".getBytes()))
            .create(DEFAULT_USER.getUsername());

    // when
    engine
        .batchOperation()
        .newLifecycle()
        .withBatchOperationKey(batchOperation.getKey())
        .suspend(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.batchOperationLifecycleRecords(BatchOperationIntent.SUSPENDED)
            .withBatchOperationKey(batchOperation.getKey())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInBatchOperationResumedEvents() {
    // given
    final var batchOperation =
        engine
            .batchOperation()
            .newCreation(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .withFilter(new UnsafeBuffer("{\"hasIncident\": false}".getBytes()))
            .create(DEFAULT_USER.getUsername());

    engine
        .batchOperation()
        .newLifecycle()
        .withBatchOperationKey(batchOperation.getKey())
        .suspend(DEFAULT_USER.getUsername());

    // when
    engine
        .batchOperation()
        .newLifecycle()
        .withBatchOperationKey(batchOperation.getKey())
        .resume(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.batchOperationLifecycleRecords(BatchOperationIntent.RESUMED)
            .withBatchOperationKey(batchOperation.getKey())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  private void assertAuthorizationClaims(final java.util.Optional<?> record) {
    assertThat(record).isPresent();
    assertThat(((io.camunda.zeebe.protocol.record.Record<?>) record.get()).getAuthorizations())
        .containsEntry(Authorization.AUTHORIZED_USERNAME, DEFAULT_USER.getUsername());
  }
}
