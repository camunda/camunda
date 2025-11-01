/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.config.ExporterConfiguration.AuditLogConfiguration;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.entities.operation.AuditLogEntity;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ProcessInstanceModificationAuditLogHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-audit";
  private final ProcessInstanceModificationAuditLogHandler underTest =
      new ProcessInstanceModificationAuditLogHandler(
          indexName, new AuditLogConfiguration(), TestObjectMapper.objectMapper());

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE_MODIFICATION);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(AuditLogEntity.class);
  }

  @Test
  void shouldHandleModifiedRecord() {
    // given
    final Record<ProcessInstanceModificationRecordValue> piModificationrecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MODIFICATION,
            b -> b.withAuthorizations(Map.of(Authorization.AUTHORIZED_USERNAME, "test-user")),
            ProcessInstanceModificationIntent.MODIFIED);

    // when - then
    assertThat(underTest.handlesRecord(piModificationrecord)).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<ProcessInstanceModificationRecordValue> piModificationrecord =
        factory.generateRecordWithIntent(
            ValueType.PROCESS_INSTANCE_MODIFICATION, ProcessInstanceModificationIntent.MODIFIED);

    // when
    final var idList = underTest.generateIds(piModificationrecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(piModificationrecord.getPosition()));
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final String recordKey = "123L";
    final Record<ProcessInstanceModificationRecordValue> piModificationRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_MODIFICATION,
            b -> b.withAuthorizations(Map.of(Authorization.AUTHORIZED_CLIENT_ID, "test-client")),
            ProcessInstanceModificationIntent.MODIFIED);
    final var piModificationRecordValue = piModificationRecord.getValue();

    // when
    final var auditLogEntity =
        new AuditLogEntity()
            .setId(recordKey)
            .setEntityKey(String.valueOf(piModificationRecord.getKey()))
            .setEntityType(piModificationRecord.getValueType().name());

    underTest.updateEntity(piModificationRecord, auditLogEntity);

    // then
    assertThat(auditLogEntity.getEntityKey())
        .isEqualTo(String.valueOf(piModificationRecord.getKey()));
    assertThat(auditLogEntity.getEntityType())
        .isEqualTo(piModificationRecord.getValueType().name());
  }

  @Test
  void shouldAddEntityOnFlush() throws PersistenceException {
    // given
    final var inputEntity = new AuditLogEntity().setId("111");
    final var mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }
}
