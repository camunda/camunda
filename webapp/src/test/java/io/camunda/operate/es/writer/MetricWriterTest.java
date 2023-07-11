/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.es.writer;

import io.camunda.operate.entities.MetricEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.indices.MetricIndex;
import io.camunda.operate.store.BatchRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static io.camunda.operate.es.contract.MetricContract.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MetricWriterTest {

  @InjectMocks
  private MetricWriter subject;
  @Mock
  BatchRequest batchRequest;
  @Mock
  private MetricIndex metricIndex;

  @Test
  public void verifyRegisterProcessEventWasCalledWithRightArgument() throws PersistenceException {
    // Given
    final String key = "processInstanceKey";
    var now = OffsetDateTime.now();

    // When
    subject.registerProcessInstanceStartEvent(key, now, batchRequest);

    // Then
    verify(batchRequest).add(metricIndex.getFullQualifiedName(), new MetricEntity()
        .setEvent(EVENT_PROCESS_INSTANCE_STARTED)
        .setValue(key)
        .setEventTime(now));
  }

  @Test
  public void verifyRegisterDecisionEventWasCalledWithRightArgument() throws PersistenceException {
    // Given
    final String key = "decisionInstanceKey";
    var now = OffsetDateTime.now();

    // When
    subject.registerDecisionInstanceCompleteEvent(key, now, batchRequest);

    // Then
    verify(batchRequest).add( metricIndex.getFullQualifiedName(), new MetricEntity()
        .setEvent(EVENT_DECISION_INSTANCE_EVALUATED)
        .setValue(key)
        .setEventTime(now));
  }
}
