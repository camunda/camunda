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
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.db.rdbms.write.service.IncidentWriter;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncidentExportHandlerTest {

  private static final int MAX_TREE_PATH_SIZE = 4096;

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private IncidentWriter incidentWriter;
  @Mock private ExporterEntityCache<Long, CachedProcessEntity> processCache;
  @Captor private ArgumentCaptor<IncidentDbModel> incidentCaptor;

  private IncidentExportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new IncidentExportHandler(incidentWriter, processCache, MAX_TREE_PATH_SIZE);
  }

  // Regression test for https://github.com/camunda/camunda/issues/50014.
  @Test
  void shouldUpdateTreePathWhenCallActivityIsNestedInAnotherFlowScope() {
    // given
    final long parentProcessDefinitionKey = 999L;
    final long parentPiKey = 111L;
    final long parentSubprocessFnInstanceKey = 122L;
    final long callActivityFnInstanceKey = 123L;
    final int callActivityIndex = 0;
    final String callActivityId = "callActivity";
    final long childPiKey = 222L;
    final long leafFnInstanceKey = 234L;
    final long childProcessDefinitionKey = 888L;

    final IncidentRecordValue value =
        ImmutableIncidentRecordValue.builder()
            .from(factory.generateObject(IncidentRecordValue.class))
            .withProcessInstanceKey(childPiKey)
            .withElementInstanceKey(leafFnInstanceKey)
            .withElementId("userTask")
            .withElementInstancePath(
                List.of(
                    List.of(parentPiKey, parentSubprocessFnInstanceKey, callActivityFnInstanceKey),
                    List.of(childPiKey, leafFnInstanceKey)))
            .withCallingElementPath(List.of(callActivityIndex))
            .withProcessDefinitionPath(
                List.of(parentProcessDefinitionKey, childProcessDefinitionKey))
            .build();

    final Record<IncidentRecordValue> record =
        factory.generateRecord(
            ValueType.INCIDENT, r -> r.withIntent(IncidentIntent.CREATED).withValue(value));

    when(processCache.get(parentProcessDefinitionKey))
        .thenReturn(
            Optional.of(
                new CachedProcessEntity(
                    null, 1, null, List.of(callActivityId), Map.of(), false, Map.of(), Map.of())));

    // when
    handler.export(record);

    // then
    verify(incidentWriter).create(incidentCaptor.capture());
    assertThat(incidentCaptor.getValue().treePath())
        .isEqualTo("PI_111/FN_callActivity/FNI_123/PI_222/FN_userTask/FNI_234");
  }
}
