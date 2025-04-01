/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.OperationStore;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.beans.factory.BeanFactory;

@ExtendWith(MockitoExtension.class)
class OperationsManagerTest {

  @Mock BeanFactory beanFactory;
  @Mock BatchOperationTemplate batchOperationTemplate;
  @Mock OperationTemplate operationTemplate;
  @Mock OperationStore operationStore;

  @Test
  void shouldSetCompletedDateToOperationWhenOperationWillComplete() throws PersistenceException {
    // given
    when(operationTemplate.getAlias()).thenReturn("operation_alias");
    final var batchRequest = mock(BatchRequest.class);
    when(beanFactory.getBean(BatchRequest.class)).thenReturn(batchRequest);
    // when
    new OperationsManager(beanFactory, batchOperationTemplate, operationTemplate, operationStore)
        .completeOperation(new OperationEntity().setId("id"));
    // then
    verify(batchRequest)
        .updateWithScript(
            any(),
            anyString(),
            contains("ctx._source.completedDate = params.now;"),
            argThat(
                m -> m.containsKey("now") && m.get("now").getClass().equals(OffsetDateTime.class)));
  }

  @Test
  void shouldCreateRequestForBatchOperation() throws PersistenceException {
    // given
    when(batchOperationTemplate.getAlias()).thenReturn("batch-operation_alias");
    final var batchRequest = mock(BatchRequest.class);
    when(operationStore.getIndexNameForAliasAndIds("batch-operation_alias", List.of("Exist")))
        .thenReturn(Map.of("Exist", "batch-operation-index"));
    final var operationManager =
        new OperationsManager(
            beanFactory, batchOperationTemplate, operationTemplate, operationStore);
    operationManager.updateFinishedInBatchOperation("Exist", batchRequest);
    // then
    verify(batchRequest)
        .updateWithScript(
            eq("batch-operation-index"),
            eq("Exist"),
            contains("ctx._source.endDate = params.now;"),
            argThat(
                m -> m.containsKey("now") && m.get("now").getClass().equals(OffsetDateTime.class)));
  }

  @Test // https://github.com/camunda/operate/issues/6077
  void shouldNotCreateRequestForMissingBatchOperation() throws PersistenceException {
    // given
    final var logger = mock(Logger.class);
    when(batchOperationTemplate.getAlias()).thenReturn("batch-operation_alias");
    final var batchRequest = mock(BatchRequest.class);
    final var ids2indexNames = new HashMap<String, String>();
    ids2indexNames.put("Does not exist", null);
    when(operationStore.getIndexNameForAliasAndIds(
            "batch-operation_alias", List.of("Does not exist")))
        .thenReturn(ids2indexNames);
    final var operationManager =
        new OperationsManager(
            logger, beanFactory, batchOperationTemplate, operationTemplate, operationStore);
    operationManager.updateFinishedInBatchOperation("Does not exist", batchRequest);
    // then
    verify(batchRequest, never())
        .updateWithScript(
            isNull(),
            eq("Does not exist"),
            contains("ctx._source.endDate = params.now;"),
            argThat(
                m -> m.containsKey("now") && m.get("now").getClass().equals(OffsetDateTime.class)));
    verify(logger)
        .warn(
            "No index found for batchOperationId={}. Skip adding an update request.",
            "Does not exist");
  }
}
