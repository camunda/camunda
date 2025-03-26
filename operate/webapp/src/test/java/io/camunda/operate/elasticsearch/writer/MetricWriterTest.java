/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch.writer;

import static io.camunda.operate.store.MetricsStore.*;
import static org.mockito.Mockito.*;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.MetricsStore;
import io.camunda.operate.store.elasticsearch.ElasticsearchMetricsStore;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.entities.MetricEntity;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MetricWriterTest {

  @Mock BatchRequest batchRequest;
  @InjectMocks private MetricsStore subject = new ElasticsearchMetricsStore();
  @Mock private MetricIndex metricIndex;

  @Test
  public void verifyRegisterProcessEventWasCalledWithRightArgument() throws PersistenceException {
    // Given
    final String key = "processInstanceKey";
    final var now = OffsetDateTime.now();
    final String tenantId = "tenantA";

    // When
    subject.registerProcessInstanceStartEvent(key, tenantId, now, batchRequest);

    // Then
    verify(batchRequest)
        .add(
            metricIndex.getFullQualifiedName(),
            new MetricEntity()
                .setEvent(EVENT_PROCESS_INSTANCE_STARTED)
                .setValue(key)
                .setEventTime(now)
                .setTenantId(tenantId));
  }

  @Test
  public void verifyRegisterDecisionEventWasCalledWithRightArgument() throws PersistenceException {
    // Given
    final String key = "decisionInstanceKey";
    final var now = OffsetDateTime.now();
    final String tenantId = "tenantA";

    // When
    subject.registerDecisionInstanceCompleteEvent(key, tenantId, now, batchRequest);

    // Then
    verify(batchRequest)
        .add(
            metricIndex.getFullQualifiedName(),
            new MetricEntity()
                .setEvent(EVENT_DECISION_INSTANCE_EVALUATED)
                .setValue(key)
                .setEventTime(now)
                .setTenantId(tenantId));
  }
}
