/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapps.schema.entities.auditlog.AuditLogActorType;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditLogInfoTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Test
  void shouldMapActorFromUsername() {
    final var record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MODIFICATION,
            r ->
                r.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                    .withAuthorizations(Map.of(Authorization.AUTHORIZED_USERNAME, "test-user")));

    final var info = AuditLogInfo.of(record);

    assertThat(info.actor()).isNotNull();
    assertThat(info.actor().actorType()).isEqualTo(AuditLogActorType.USER);
    assertThat(info.actor().actorId()).isEqualTo("test-user");
  }

  @Test
  void shouldMapActorFromClientId() {
    final var record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MODIFICATION,
            r ->
                r.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                    .withAuthorizations(Map.of(Authorization.AUTHORIZED_CLIENT_ID, "test-client")));

    final var info = AuditLogInfo.of(record);

    assertThat(info.actor()).isNotNull();
    assertThat(info.actor().actorType()).isEqualTo(AuditLogActorType.CLIENT);
    assertThat(info.actor().actorId()).isEqualTo("test-client");
  }

  @Test
  void shouldReturnNullActorWhenNoAuthorizationProvided() {
    final var record =
        factory.generateRecordWithIntent(
            ValueType.PROCESS_INSTANCE_MODIFICATION, ProcessInstanceModificationIntent.MODIFIED);

    final var info = AuditLogInfo.of(record);

    assertThat(info.actor()).isNull();
  }

  @Test
  void shouldMapBatchOperationWhenPresent() {
    final var record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MODIFICATION,
            r ->
                r.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                    .withBatchOperationReference(12345L));

    final var info = AuditLogInfo.of(record);

    assertThat(info.batchOperation()).isPresent();
    assertThat(info.batchOperation().get().key()).isEqualTo(12345L);
  }

  @Test
  void shouldReturnEmptyBatchOperationWhenNotPresent() {
    final var record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MODIFICATION,
            r ->
                r.withIntent(ProcessInstanceModificationIntent.MODIFIED)
                    .withBatchOperationReference(
                        RecordMetadataDecoder.batchOperationReferenceNullValue()));

    final var info = AuditLogInfo.of(record);

    assertThat(info.batchOperation()).isEmpty();
  }
}
