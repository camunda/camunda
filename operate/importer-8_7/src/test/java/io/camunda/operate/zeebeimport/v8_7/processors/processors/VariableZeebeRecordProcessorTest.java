/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.v8_7.processors.processors;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.ImportProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.zeebeimport.processors.VariableZeebeRecordProcessor;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VariableZeebeRecordProcessorTest {

  private static final int VARIABLE_SIZE_THRESHOLD = 8;
  private static final String VARIABLE_INDEX = "variable-index";

  @Mock private VariableTemplate variableTemplate;
  @Mock private OperateProperties operateProperties;
  @Mock private ImportProperties importProperties;
  @InjectMocks private VariableZeebeRecordProcessor variableZeebeRecordProcessor;

  @BeforeEach
  void setUp() {
    when(operateProperties.getImporter()).thenReturn(importProperties);
    when(importProperties.getVariableSizeThreshold()).thenReturn(VARIABLE_SIZE_THRESHOLD);
  }

  @Test
  void shouldStorePreviewAndFullValueWhenValueExceedsThreshold() throws PersistenceException {
    // given a value longer than the threshold
    final String value = "\"123456789\""; // 11 chars > 8

    // when
    final VariableEntity entity = process(value);

    // then the stored value is truncated and the full value is kept as preview
    assertThat(entity.getIsPreview()).isTrue();
    assertThat(entity.getValue())
        .hasSize(VARIABLE_SIZE_THRESHOLD)
        .isEqualTo(value.substring(0, VARIABLE_SIZE_THRESHOLD));
    assertThat(entity.getFullValue()).isEqualTo(value);
  }

  @Test
  void shouldNotStorePreviewWhenValueWithinThreshold() throws PersistenceException {
    // given a value shorter than the threshold
    final String value = "\"123\""; // 5 chars < 8

    // when
    final VariableEntity entity = process(value);

    // then the value is stored as-is with no preview
    assertThat(entity.getIsPreview()).isFalse();
    assertThat(entity.getValue()).isEqualTo(value);
    assertThat(entity.getFullValue()).isNull();
  }

  @Test
  void shouldNotStorePreviewWhenValueEqualsThreshold() throws PersistenceException {
    // given a value exactly at the threshold (truncation only applies when strictly greater)
    final String value = "12345678"; // 8 chars == 8

    // when
    final VariableEntity entity = process(value);

    // then no preview is stored
    assertThat(entity.getIsPreview()).isFalse();
    assertThat(entity.getValue()).isEqualTo(value);
    assertThat(entity.getFullValue()).isNull();
  }

  @SuppressWarnings("unchecked")
  private VariableEntity process(final String value) throws PersistenceException {
    // given
    final var batchRequest = mock(BatchRequest.class);
    final var record = (Record<VariableRecordValue>) mock(Record.class);
    final var recordValue =
        ImmutableVariableRecordValue.builder()
            .withScopeKey(1L)
            .withProcessInstanceKey(2L)
            .withProcessDefinitionKey(3L)
            .withBpmnProcessId("process")
            .withName("var")
            .withValue(value)
            .withTenantId(DEFAULT_TENANT_ID)
            .build();
    when(variableTemplate.getFullQualifiedName()).thenReturn(VARIABLE_INDEX);
    when(record.getIntent()).thenReturn(VariableIntent.CREATED);
    when(record.getValue()).thenReturn(recordValue);

    // when
    variableZeebeRecordProcessor.processVariableRecords(Map.of(1L, List.of(record)), batchRequest);

    // then capture the persisted entity
    final ArgumentCaptor<VariableEntity> captor = ArgumentCaptor.forClass(VariableEntity.class);
    verify(batchRequest).upsert(eq(VARIABLE_INDEX), anyString(), captor.capture(), anyMap());
    return captor.getValue();
  }
}
