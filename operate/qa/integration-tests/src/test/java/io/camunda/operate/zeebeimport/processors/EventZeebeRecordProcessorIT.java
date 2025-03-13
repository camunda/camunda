/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.processors;

import static io.camunda.operate.util.TestUtil.createEvent;
import static io.camunda.operate.util.ZeebeRecordTestUtil.createProcessMessageSubscriptionZeebeRecord;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.EventEntity;
import io.camunda.operate.entities.EventMetadataEntity;
import io.camunda.operate.entities.EventSourceType;
import io.camunda.operate.entities.EventType;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.EventTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class EventZeebeRecordProcessorIT extends OperateSearchAbstractIT {

  @Autowired private EventTemplate eventTemplate;
  @Autowired private EventZeebeRecordProcessor eventZeebeRecordProcessor;
  @Autowired private BeanFactory beanFactory;
  @MockBean private PartitionHolder partitionHolder;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    when(partitionHolder.getPartitionIds()).thenReturn(List.of(1));
  }

  @Test
  public void shouldImportMessageSubscriptionMigratedEvent()
      throws IOException, PersistenceException {
    // given
    final EventEntity event = createEvent().setPositionProcessMessageSubscription(1L);
    event.setEventType(EventType.CREATED);

    final EventMetadataEntity metadata = new EventMetadataEntity();
    event.setMetadata(metadata);

    metadata.setMessageName("message-1");
    metadata.setCorrelationKey("corr-key-1");

    final String updatedMessageName = "message-2";
    final String updatedCorrelationKey = "corr-key-2";

    testSearchRepository.createOrUpdateDocumentFromObject(
        eventTemplate.getFullQualifiedName(), event.getId(), event);

    final Record<ProcessMessageSubscriptionRecordValue> zeebeRecord =
        createProcessMessageSubscriptionZeebeRecord(
            b -> b.withIntent(ProcessMessageSubscriptionIntent.MIGRATED),
            b ->
                b.withProcessInstanceKey(event.getProcessInstanceKey())
                    .withElementInstanceKey(event.getFlowNodeInstanceKey())
                    .withMessageName(updatedMessageName)
                    .withCorrelationKey(updatedCorrelationKey));

    // when
    importProcessMessageSubscriptionZeebeRecord(zeebeRecord);

    // then
    final EventEntity updatedEvent = findEventById(event.getId());

    // the subscription properties were updated
    assertThat(updatedEvent.getEventType()).isEqualTo(EventType.MIGRATED);
    assertThat(updatedEvent.getMetadata().getMessageName()).isEqualTo(updatedMessageName);
    assertThat(updatedEvent.getMetadata().getCorrelationKey()).isEqualTo(updatedCorrelationKey);

    // and the other properties are still the same
    assertThat(updatedEvent.getId()).isEqualTo(event.getId());
    assertThat(updatedEvent.getTenantId()).isEqualTo(event.getTenantId());
    assertThat(updatedEvent.getProcessInstanceKey()).isEqualTo(event.getProcessInstanceKey());
    assertThat(updatedEvent.getFlowNodeInstanceKey()).isEqualTo(event.getFlowNodeInstanceKey());
    assertThat(updatedEvent.getEventSourceType())
        .isEqualTo(EventSourceType.PROCESS_MESSAGE_SUBSCRIPTION);
    assertThat(updatedEvent.getDateTime()).isNotNull();
  }

  @NotNull
  private EventEntity findEventById(final String id) throws IOException {
    final List<EventEntity> entities =
        testSearchRepository.searchTerm(
            eventTemplate.getFullQualifiedName(), "_id", id, EventEntity.class, 10);
    final Optional<EventEntity> first = entities.stream().findFirst();
    assertThat(first.isPresent()).isTrue();
    return first.get();
  }

  private void importProcessMessageSubscriptionZeebeRecord(
      final Record<ProcessMessageSubscriptionRecordValue> zeebeRecord) throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    eventZeebeRecordProcessor.processProcessMessageSubscription(
        Map.of(zeebeRecord.getValue().getElementInstanceKey(), List.of(zeebeRecord)), batchRequest);
    batchRequest.execute();
    searchContainerManager.refreshIndices(eventTemplate.getFullQualifiedName());
  }
}
