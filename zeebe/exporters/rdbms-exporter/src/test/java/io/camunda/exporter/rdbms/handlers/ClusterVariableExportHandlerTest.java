/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.write.domain.ClusterVariableDbModel;
import io.camunda.db.rdbms.write.service.ClusterVariableWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.value.ClusterVariableScope;
import io.camunda.zeebe.protocol.record.value.ImmutableClusterVariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClusterVariableExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private ClusterVariableWriter writer;
  @Captor private ArgumentCaptor<ClusterVariableDbModel> modelCaptor;

  private ClusterVariableExportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new ClusterVariableExportHandler(writer);
  }

  @Test
  void shouldExportIntegerBeyondLongRange() {
    // given
    final var recordValue =
        ImmutableClusterVariableRecordValue.builder()
            .withName("myVariable")
            .withValue("18446744073709551615")
            .withScope(ClusterVariableScope.GLOBAL)
            .build();
    final Record<io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue> record =
        factory.generateRecord(
            ValueType.CLUSTER_VARIABLE,
            r -> r.withIntent(ClusterVariableIntent.CREATED).withValue(recordValue));

    // when
    handler.export(record);

    // then
    verify(writer).create(modelCaptor.capture());
    assertThat(modelCaptor.getValue().value()).isEqualTo("18446744073709551615");
  }
}
