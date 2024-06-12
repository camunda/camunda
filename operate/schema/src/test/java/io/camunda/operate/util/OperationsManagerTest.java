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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.OperationStore;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
}
