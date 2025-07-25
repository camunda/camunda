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

import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.usermanagement.MappingRuleEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableMappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

public class MappingRuleCreatedUpdatedHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-mapping-rule";
  private final MappingRuleCreatedUpdatedHandler underTest =
      new MappingRuleCreatedUpdatedHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.MAPPING_RULE);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(MappingRuleEntity.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = MappingRuleIntent.class,
      names = {"CREATED", "UPDATED"},
      mode = Mode.INCLUDE)
  void shouldHandleRecord(final MappingRuleIntent intent) {
    // given
    final Record<MappingRuleRecordValue> mappingRuleRecord =
        factory.generateRecordWithIntent(ValueType.MAPPING_RULE, intent);

    // when - then
    assertThat(underTest.handlesRecord(mappingRuleRecord)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = MappingRuleIntent.class,
      names = {"CREATED", "UPDATED"},
      mode = Mode.INCLUDE)
  void shouldGenerateIds(final MappingRuleIntent intent) {
    // given
    final Record<MappingRuleRecordValue> mappingRuleRecord =
        factory.generateRecordWithIntent(ValueType.MAPPING_RULE, intent);

    // when
    final var idList = underTest.generateIds(mappingRuleRecord);

    // then
    assertThat(idList)
        .containsExactly(String.valueOf(mappingRuleRecord.getValue().getMappingRuleId()));
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
    final MappingRuleRecordValue mappingRuleRecordValue =
        ImmutableMappingRuleRecordValue.builder()
            .from(factory.generateObject(MappingRuleRecordValue.class))
            .withClaimName("updated-claim")
            .withClaimValue("updated-value")
            .withName("updated-name")
            .withMappingRuleId("updated-id")
            .build();

    final Record<MappingRuleRecordValue> mappingRuleRecord =
        factory.generateRecord(
            ValueType.MAPPING_RULE,
            r -> r.withIntent(MappingRuleIntent.UPDATED).withValue(mappingRuleRecordValue));

    // when
    final MappingRuleEntity mappingRuleEntity =
        new MappingRuleEntity()
            .setClaimName("old-claim")
            .setClaimValue("old-value")
            .setName("old-name")
            .setId("old-id");
    underTest.updateEntity(mappingRuleRecord, mappingRuleEntity);

    // then
    assertThat(mappingRuleEntity.getClaimName()).isEqualTo("updated-claim");
    assertThat(mappingRuleEntity.getClaimValue()).isEqualTo("updated-value");
    assertThat(mappingRuleEntity.getName()).isEqualTo("updated-name");
    assertThat(mappingRuleEntity.getMappingRuleId()).isEqualTo("updated-id");
  }

  @Test
  void shouldAddEntityOnFlush() throws PersistenceException {
    // given
    final MappingRuleEntity inputEntity = new MappingRuleEntity().setId("111");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }
}
