/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchOperationItemEntityTransformerTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  private final BatchOperationItemEntityTransformer transformer =
      new BatchOperationItemEntityTransformer();

  @Test
  void shouldTransformEntityToSearchEntity() {
    // given
    final OperationEntity entity = new OperationEntity();
    entity.setBatchOperationId("1");
    entity.setType(OperationType.CANCEL_PROCESS_INSTANCE);
    entity.setState(OperationState.SCHEDULED);
    entity.setItemKey(123L);
    entity.setProcessInstanceKey(456L);
    entity.setCompletedDate(NOW);
    entity.setErrorMessage("error");

    // when
    final var searchEntity = transformer.apply(entity);
    assertThat(searchEntity).isNotNull();
    assertThat(searchEntity.batchOperationKey()).isEqualTo("1");
    assertThat(searchEntity.state()).isEqualTo(BatchOperationItemState.ACTIVE);
    assertThat(searchEntity.itemKey()).isEqualTo(123L);
    assertThat(searchEntity.processInstanceKey()).isEqualTo(456L);
    assertThat(searchEntity.processedDate()).isEqualTo(NOW);
    assertThat(searchEntity.errorMessage()).isEqualTo("error");
  }

  @Test
  void shouldTransformLegacyEntityToSearchEntity() {
    final var uuid = UUID.randomUUID().toString();

    final OperationEntity entity = new OperationEntity();
    entity.setBatchOperationId(uuid);
    entity.setType(OperationType.CANCEL_PROCESS_INSTANCE);
    entity.setState(OperationState.SCHEDULED);
    entity.setIncidentKey(123L);
    entity.setProcessInstanceKey(456L);
    entity.setCompletedDate(NOW);
    entity.setErrorMessage("error");

    // when
    final var searchEntity = transformer.apply(entity);
    assertThat(searchEntity).isNotNull();
    assertThat(searchEntity.batchOperationKey()).isEqualTo(uuid);
    assertThat(searchEntity.state()).isEqualTo(BatchOperationItemState.ACTIVE);
    assertThat(searchEntity.itemKey()).isEqualTo(123L);
    assertThat(searchEntity.processInstanceKey()).isEqualTo(456L);
    assertThat(searchEntity.processedDate()).isEqualTo(NOW);
    assertThat(searchEntity.errorMessage()).isEqualTo("error");
  }

  @Test
  void shouldUseProcessDefinitionKeyAsItemKeyOfLegacyDeleteProcessDefinitionItem() {
    // given - legacy definition deletions set neither incidentKey nor processInstanceKey
    final OperationEntity entity = new OperationEntity();
    entity.setBatchOperationId(UUID.randomUUID().toString());
    entity.setType(OperationType.DELETE_PROCESS_DEFINITION);
    entity.setState(OperationState.COMPLETED);
    entity.setProcessDefinitionKey(789L);

    // when
    final var searchEntity = transformer.apply(entity);

    // then
    assertThat(searchEntity.itemKey()).isEqualTo(789L);
    assertThat(searchEntity.processInstanceKey()).isNull();
  }

  @Test
  void shouldUseDecisionDefinitionKeyAsItemKeyOfLegacyDeleteDecisionDefinitionItem() {
    // given - legacy definition deletions set neither incidentKey nor processInstanceKey
    final OperationEntity entity = new OperationEntity();
    entity.setBatchOperationId(UUID.randomUUID().toString());
    entity.setType(OperationType.DELETE_DECISION_DEFINITION);
    entity.setState(OperationState.COMPLETED);
    entity.setDecisionDefinitionKey(321L);

    // when
    final var searchEntity = transformer.apply(entity);

    // then
    assertThat(searchEntity.itemKey()).isEqualTo(321L);
    assertThat(searchEntity.processInstanceKey()).isNull();
  }
}
