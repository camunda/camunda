/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProbeRecordValue;
import io.camunda.zeebe.protocol.record.value.ProbeRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class ProbeAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ProbeAuditLogTransformer transformer = new ProbeAuditLogTransformer();

  @ParameterizedTest
  @CsvSource({"CREATED,CREATE", "ANSWERED,COMPLETE", "TIMED_OUT,CANCEL", "CANCELED,CANCEL"})
  void shouldAuditTheStartAndTheEndOfAProbe(
      final ProbeIntent intent, final AuditLogOperationType operationType) {
    // given
    final ProbeRecordValue value =
        ImmutableProbeRecordValue.builder()
            .from(factory.generateObject(ProbeRecordValue.class))
            .withProbeId("validate-configuration/io.camunda:aws-credential:1")
            .build();
    final Record<ProbeRecordValue> record =
        factory.generateRecord(
            ValueType.PROBE,
            r ->
                r.withKey(333L)
                    .withRecordType(RecordType.EVENT)
                    .withIntent(intent)
                    .withValue(value));

    // when
    final var entry = AuditLogEntry.of(record);
    transformer.transform(record, entry);

    // then
    assertThat(transformer.supports(record)).isTrue();
    assertThat(entry.getEntityType()).isEqualTo(AuditLogEntityType.PROBE);
    assertThat(entry.getEntityKey()).isEqualTo("333");
    assertThat(entry.getEntityDescription())
        .isEqualTo("validate-configuration/io.camunda:aws-credential:1");
    assertThat(entry.getOperationType()).isEqualTo(operationType);
  }
}
