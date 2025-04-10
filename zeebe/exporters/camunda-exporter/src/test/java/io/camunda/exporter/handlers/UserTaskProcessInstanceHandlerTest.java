/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.usertask.TaskProcessInstanceEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class UserTaskProcessInstanceHandlerTest {

  private static final List<BpmnElementType> VARIABLE_SCOPE_TYPES =
      Arrays.asList(
          BpmnElementType.PROCESS,
          BpmnElementType.SUB_PROCESS,
          BpmnElementType.EVENT_SUB_PROCESS,
          BpmnElementType.SERVICE_TASK,
          BpmnElementType.USER_TASK,
          BpmnElementType.MULTI_INSTANCE_BODY);
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-tasklist-task";
  private final UserTaskProcessInstanceHandler underTest =
      new UserTaskProcessInstanceHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(TaskProcessInstanceEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                    .withValue(
                        ImmutableProcessInstanceRecordValue.builder()
                            .withBpmnElementType(BpmnElementType.PROCESS)
                            .build()));
    // when - then
    assertThat(underTest.handlesRecord(processInstanceRecord)).isTrue();
  }

  @Test
  void shouldNotHandleRecord() {
    Arrays.stream(BpmnElementType.values())
        .filter(t -> !VARIABLE_SCOPE_TYPES.contains(t))
        .forEach(
            type -> {
              // given
              final Record<ProcessInstanceRecordValue> processInstanceRecord =
                  factory.generateRecord(
                      ValueType.PROCESS_INSTANCE,
                      r ->
                          r.withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                              .withValue(
                                  ImmutableProcessInstanceRecordValue.builder()
                                      .withBpmnElementType(type)
                                      .build()));
              // when - then
              assertThat(underTest.handlesRecord(processInstanceRecord)).isFalse();
            });
  }

  @Test
  void shouldGenerateIds() {
    // given
    final long expectedId = 123;

    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r -> r.withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING).withKey(expectedId));

    // when
    final var idList = underTest.generateIds(processInstanceRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(expectedId));
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
  void shouldAddEntityOnFlush() {
    // given
    final TaskProcessInstanceEntity inputEntity = new TaskProcessInstanceEntity().setId("111");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    final long expectedId = 123L;
    final ProcessInstanceRecordValue processInstanceRecordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .from(factory.generateObject(ProcessInstanceRecordValue.class))
            .build();

    final Record<ProcessInstanceRecordValue> processInstanceRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                    .withValue(processInstanceRecordValue)
                    .withKey(expectedId));

    // when
    final TaskProcessInstanceEntity processInstanceEntity =
        new TaskProcessInstanceEntity().setId(String.valueOf(expectedId));
    underTest.updateEntity(processInstanceRecord, processInstanceEntity);

    // then
    assertThat(processInstanceEntity.getId()).isEqualTo(String.valueOf(expectedId));
    assertThat(processInstanceEntity.getProcessInstanceId()).isEqualTo(expectedId);
    assertThat(processInstanceEntity.getTenantId())
        .isEqualTo(processInstanceRecordValue.getTenantId());
    assertThat(processInstanceEntity.getJoin()).isNotNull();
    assertThat(processInstanceEntity.getJoin().getName())
        .isEqualTo(TaskJoinRelationshipType.PROCESS.getType());
  }
}
