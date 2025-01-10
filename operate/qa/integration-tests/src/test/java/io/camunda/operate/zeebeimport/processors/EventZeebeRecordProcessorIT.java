/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.processors;

import static io.camunda.operate.util.TestUtil.createEvent;
import static io.camunda.operate.util.TestUtil.createIncident;
import static io.camunda.operate.util.ZeebeRecordTestUtil.createJobZeebeRecord;
import static io.camunda.operate.util.ZeebeRecordTestUtil.createProcessInstanceZeebeRecord;
import static io.camunda.operate.util.ZeebeRecordTestUtil.createProcessMessageSubscriptionZeebeRecord;
import static io.camunda.operate.util.ZeebeRecordTestUtil.createZeebeRecordFromIncident;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.EventEntity;
import io.camunda.operate.entities.EventMetadataEntity;
import io.camunda.operate.entities.EventSourceType;
import io.camunda.operate.entities.EventType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.EventTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
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

  private final String newBpmnProcessId = "newBpmnProcessId";
  private final long newProcessDefinitionKey = 111;
  private final String errorMsg = "errorMsg";
  @Autowired private EventTemplate eventTemplate;
  @Autowired private EventZeebeRecordProcessor eventZeebeRecordProcessor;
  @Autowired private BeanFactory beanFactory;
  @MockBean private PartitionHolder partitionHolder;
  @Autowired private ImportPositionHolder importPositionHolder;
  private final String jobType = "createOrder";
  private final String jobWorker = "someWorker";
  private final int jobRetries = 2;
  private final Map<String, String> jobCustomHeaders =
      Map.of("header1", "value1", "header2", "value2");
  private final String messageName = "clientId";
  private final String correlationKey = "123";

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    when(partitionHolder.getPartitionIds()).thenReturn(List.of(1));
  }

  @Test
  public void shouldOverrideEventIncidentFields() throws IOException, PersistenceException {
    // having
    // event entity with position = 1
    final EventEntity event = createEvent().setPositionIncident(1L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        eventTemplate.getFullQualifiedName(), event.getId(), event);

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final IncidentEntity inc = createIncident(IncidentState.ACTIVE, errorMsg);
    final Record<IncidentRecordValue> zeebeRecord =
        createZeebeRecordFromIncident(
            inc,
            b -> b.withPosition(newPosition).withIntent(IncidentIntent.CREATED),
            b ->
                b.withProcessInstanceKey(event.getProcessInstanceKey())
                    .withElementInstanceKey(event.getFlowNodeInstanceKey()));
    importIncidentZeebeRecord(zeebeRecord);

    // then
    // event fields are updated
    final EventEntity updatedEvent = findEventById(event.getId());
    // old values
    assertThat(updatedEvent.getId()).isEqualTo(event.getId());
    assertThat(updatedEvent.getTenantId()).isEqualTo(event.getTenantId());
    assertThat(updatedEvent.getProcessInstanceKey()).isEqualTo(event.getProcessInstanceKey());
    assertThat(updatedEvent.getFlowNodeInstanceKey()).isEqualTo(event.getFlowNodeInstanceKey());
    // new values
    assertThat(updatedEvent.getEventSourceType()).isEqualTo(EventSourceType.INCIDENT);
    assertThat(updatedEvent.getDateTime()).isNotNull();
    assertThat(updatedEvent.getEventType()).isEqualTo(EventType.CREATED);
    assertThat(updatedEvent.getMetadata().getIncidentErrorMessage()).isEqualTo(errorMsg);
    assertThat(updatedEvent.getPositionIncident()).isEqualTo(newPosition);
  }

  @Test
  public void shouldOverrideEventIncidentFieldsForNullPosition()
      throws IOException, PersistenceException {
    // having
    final EventEntity event = createEvent(); // null positionIncident
    testSearchRepository.createOrUpdateDocumentFromObject(
        eventTemplate.getFullQualifiedName(), event.getId(), event);

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final IncidentEntity inc = createIncident(IncidentState.ACTIVE, errorMsg);
    final Record<IncidentRecordValue> zeebeRecord =
        createZeebeRecordFromIncident(
            inc,
            b -> b.withPosition(newPosition).withIntent(IncidentIntent.CREATED),
            b ->
                b.withProcessInstanceKey(event.getProcessInstanceKey())
                    .withElementInstanceKey(event.getFlowNodeInstanceKey()));
    importIncidentZeebeRecord(zeebeRecord);

    // then
    // event fields are updated
    final EventEntity updatedEvent = findEventById(event.getId());
    // old values
    assertThat(updatedEvent.getId()).isEqualTo(event.getId());
    assertThat(updatedEvent.getTenantId()).isEqualTo(event.getTenantId());
    assertThat(updatedEvent.getProcessInstanceKey()).isEqualTo(event.getProcessInstanceKey());
    assertThat(updatedEvent.getFlowNodeInstanceKey()).isEqualTo(event.getFlowNodeInstanceKey());
    // new values
    assertThat(updatedEvent.getEventSourceType()).isEqualTo(EventSourceType.INCIDENT);
    assertThat(updatedEvent.getDateTime()).isNotNull();
    assertThat(updatedEvent.getEventType()).isEqualTo(EventType.CREATED);
    assertThat(updatedEvent.getMetadata().getIncidentErrorMessage()).isEqualTo(errorMsg);
    assertThat(updatedEvent.getPositionIncident()).isEqualTo(newPosition);
  }

  @Test
  public void shouldNotOverrideEventIncidentFields() throws IOException, PersistenceException {
    // having
    // event entity with position = 2
    final long oldPosition = 2L;
    final EventEntity event = createEvent().setPositionIncident(oldPosition);
    testSearchRepository.createOrUpdateDocumentFromObject(
        eventTemplate.getFullQualifiedName(), event.getId(), event);

    // when
    // importing Zeebe record with smaller position
    final long newPosition = 1L;
    final IncidentEntity inc = createIncident(IncidentState.ACTIVE, errorMsg);
    final Record<IncidentRecordValue> zeebeRecord =
        createZeebeRecordFromIncident(
            inc,
            b -> b.withPosition(newPosition).withIntent(IncidentIntent.CREATED),
            b ->
                b.withProcessInstanceKey(event.getProcessInstanceKey())
                    .withElementInstanceKey(event.getFlowNodeInstanceKey()));
    importIncidentZeebeRecord(zeebeRecord);

    // then
    // event fields are NOT updated
    final EventEntity updatedEvent = findEventById(event.getId());
    // old values
    assertThat(updatedEvent.getId()).isEqualTo(event.getId());
    assertThat(updatedEvent.getTenantId()).isEqualTo(event.getTenantId());
    assertThat(updatedEvent.getProcessInstanceKey()).isEqualTo(event.getProcessInstanceKey());
    assertThat(updatedEvent.getFlowNodeInstanceKey()).isEqualTo(event.getFlowNodeInstanceKey());
    // old values
    assertThat(updatedEvent.getEventSourceType()).isEqualTo(event.getEventSourceType());
    assertThat(updatedEvent.getDateTime()).isEqualTo(event.getEventSourceType());
    assertThat(updatedEvent.getEventType()).isEqualTo(event.getEventType());
    assertThat(updatedEvent.getMetadata()).isNull();
    assertThat(updatedEvent.getPositionIncident()).isEqualTo(oldPosition);
  }

  @Test
  public void shouldOverrideEventJobFields() throws IOException, PersistenceException {
    // having
    // event entity with position = 1
    final EventEntity event = createEvent().setPositionJob(1L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        eventTemplate.getFullQualifiedName(), event.getId(), event);

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<JobRecordValue> zeebeRecord =
        createJobZeebeRecord(
            b -> b.withPosition(newPosition).withIntent(JobIntent.CREATED),
            b ->
                b.withProcessInstanceKey(event.getProcessInstanceKey())
                    .withElementInstanceKey(event.getFlowNodeInstanceKey())
                    .withType(jobType)
                    .withWorker(jobWorker)
                    .withRetries(jobRetries)
                    .withCustomHeaders(jobCustomHeaders));
    importJobZeebeRecord(zeebeRecord);

    // then
    // event fields are updated
    final EventEntity updatedEvent = findEventById(event.getId());
    // old values
    assertThat(updatedEvent.getId()).isEqualTo(event.getId());
    assertThat(updatedEvent.getTenantId()).isEqualTo(event.getTenantId());
    assertThat(updatedEvent.getProcessInstanceKey()).isEqualTo(event.getProcessInstanceKey());
    assertThat(updatedEvent.getFlowNodeInstanceKey()).isEqualTo(event.getFlowNodeInstanceKey());
    // new values
    assertThat(updatedEvent.getEventSourceType()).isEqualTo(EventSourceType.JOB);
    assertThat(updatedEvent.getDateTime()).isNotNull();
    assertThat(updatedEvent.getEventType()).isEqualTo(EventType.CREATED);
    assertThat(updatedEvent.getMetadata().getJobType()).isEqualTo(jobType);
    assertThat(updatedEvent.getMetadata().getJobWorker()).isEqualTo(jobWorker);
    assertThat(updatedEvent.getMetadata().getJobRetries()).isEqualTo(jobRetries);
    assertThat(updatedEvent.getMetadata().getJobCustomHeaders()).isEqualTo(jobCustomHeaders);
    assertThat(updatedEvent.getPositionJob()).isEqualTo(newPosition);
  }

  @Test
  public void shouldOverrideEventJobFieldsForNullPosition()
      throws IOException, PersistenceException {
    // having
    final EventEntity event = createEvent(); // null position
    testSearchRepository.createOrUpdateDocumentFromObject(
        eventTemplate.getFullQualifiedName(), event.getId(), event);

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<JobRecordValue> zeebeRecord =
        createJobZeebeRecord(
            b -> b.withPosition(newPosition).withIntent(JobIntent.CREATED),
            b ->
                b.withProcessInstanceKey(event.getProcessInstanceKey())
                    .withElementInstanceKey(event.getFlowNodeInstanceKey())
                    .withType(jobType)
                    .withWorker(jobWorker)
                    .withRetries(jobRetries)
                    .withCustomHeaders(jobCustomHeaders));
    importJobZeebeRecord(zeebeRecord);

    // then
    // event fields are updated
    final EventEntity updatedEvent = findEventById(event.getId());
    // old values
    assertThat(updatedEvent.getId()).isEqualTo(event.getId());
    assertThat(updatedEvent.getTenantId()).isEqualTo(event.getTenantId());
    assertThat(updatedEvent.getProcessInstanceKey()).isEqualTo(event.getProcessInstanceKey());
    assertThat(updatedEvent.getFlowNodeInstanceKey()).isEqualTo(event.getFlowNodeInstanceKey());
    // new values
    assertThat(updatedEvent.getEventSourceType()).isEqualTo(EventSourceType.JOB);
    assertThat(updatedEvent.getDateTime()).isNotNull();
    assertThat(updatedEvent.getEventType()).isEqualTo(EventType.CREATED);
    assertThat(updatedEvent.getMetadata().getJobType()).isEqualTo(jobType);
    assertThat(updatedEvent.getMetadata().getJobWorker()).isEqualTo(jobWorker);
    assertThat(updatedEvent.getMetadata().getJobRetries()).isEqualTo(jobRetries);
    assertThat(updatedEvent.getMetadata().getJobCustomHeaders()).isEqualTo(jobCustomHeaders);
    assertThat(updatedEvent.getPositionJob()).isEqualTo(newPosition);
  }

  @Test
  public void shouldNotOverrideEventJobFields() throws IOException, PersistenceException {
    // having
    // event entity with position = 2
    final long oldPosition = 2L;
    final EventEntity event = createEvent().setPositionJob(oldPosition);
    testSearchRepository.createOrUpdateDocumentFromObject(
        eventTemplate.getFullQualifiedName(), event.getId(), event);

    // when
    // importing Zeebe record with smaller position
    final long newPosition = 1L;
    final Record<JobRecordValue> zeebeRecord =
        createJobZeebeRecord(
            b -> b.withPosition(newPosition).withIntent(JobIntent.CREATED),
            b ->
                b.withProcessInstanceKey(event.getProcessInstanceKey())
                    .withElementInstanceKey(event.getFlowNodeInstanceKey())
                    .withType(jobType)
                    .withWorker(jobWorker)
                    .withRetries(jobRetries)
                    .withCustomHeaders(jobCustomHeaders));
    importJobZeebeRecord(zeebeRecord);

    // then
    // event fields are updated
    final EventEntity updatedEvent = findEventById(event.getId());
    // old values
    assertThat(updatedEvent.getId()).isEqualTo(event.getId());
    assertThat(updatedEvent.getTenantId()).isEqualTo(event.getTenantId());
    assertThat(updatedEvent.getProcessInstanceKey()).isEqualTo(event.getProcessInstanceKey());
    assertThat(updatedEvent.getFlowNodeInstanceKey()).isEqualTo(event.getFlowNodeInstanceKey());
    // old values
    assertThat(updatedEvent.getEventSourceType()).isEqualTo(event.getEventSourceType());
    assertThat(updatedEvent.getDateTime()).isEqualTo(event.getDateTime());
    assertThat(updatedEvent.getEventType()).isEqualTo(event.getEventType());
    assertThat(updatedEvent.getMetadata()).isNull();
    assertThat(updatedEvent.getPositionJob()).isEqualTo(oldPosition);
  }

  @Test
  public void shouldOverrideEventProcessMessageFields() throws IOException, PersistenceException {
    // having
    // event entity with position = 1
    final EventEntity event = createEvent().setPositionProcessMessageSubscription(1L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        eventTemplate.getFullQualifiedName(), event.getId(), event);

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<ProcessMessageSubscriptionRecordValue> zeebeRecord =
        createProcessMessageSubscriptionZeebeRecord(
            b -> b.withPosition(newPosition).withIntent(ProcessMessageSubscriptionIntent.CREATED),
            b ->
                b.withProcessInstanceKey(event.getProcessInstanceKey())
                    .withElementInstanceKey(event.getFlowNodeInstanceKey())
                    .withMessageName(messageName)
                    .withCorrelationKey(correlationKey));
    importProcessMessageSubscriptionZeebeRecord(zeebeRecord);

    // then
    // event fields are updated
    final EventEntity updatedEvent = findEventById(event.getId());
    // old values
    assertThat(updatedEvent.getId()).isEqualTo(event.getId());
    assertThat(updatedEvent.getTenantId()).isEqualTo(event.getTenantId());
    assertThat(updatedEvent.getProcessInstanceKey()).isEqualTo(event.getProcessInstanceKey());
    assertThat(updatedEvent.getFlowNodeInstanceKey()).isEqualTo(event.getFlowNodeInstanceKey());
    // new values
    assertThat(updatedEvent.getEventSourceType())
        .isEqualTo(EventSourceType.PROCESS_MESSAGE_SUBSCRIPTION);
    assertThat(updatedEvent.getDateTime()).isNotNull();
    assertThat(updatedEvent.getEventType()).isEqualTo(EventType.CREATED);
    assertThat(updatedEvent.getMetadata().getMessageName()).isEqualTo(messageName);
    assertThat(updatedEvent.getMetadata().getCorrelationKey()).isEqualTo(correlationKey);
    assertThat(updatedEvent.getPositionProcessMessageSubscription()).isEqualTo(newPosition);
  }

  @Test
  public void shouldOverrideEventProcessMessageFieldsForNullPosition()
      throws IOException, PersistenceException {
    // having
    // event entity with null position
    final EventEntity event = createEvent(); // null position
    testSearchRepository.createOrUpdateDocumentFromObject(
        eventTemplate.getFullQualifiedName(), event.getId(), event);

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<ProcessMessageSubscriptionRecordValue> zeebeRecord =
        createProcessMessageSubscriptionZeebeRecord(
            b -> b.withPosition(newPosition).withIntent(ProcessMessageSubscriptionIntent.CREATED),
            b ->
                b.withProcessInstanceKey(event.getProcessInstanceKey())
                    .withElementInstanceKey(event.getFlowNodeInstanceKey())
                    .withMessageName(messageName)
                    .withCorrelationKey(correlationKey));
    importProcessMessageSubscriptionZeebeRecord(zeebeRecord);

    // then
    // event fields are updated
    final EventEntity updatedEvent = findEventById(event.getId());
    // old values
    assertThat(updatedEvent.getId()).isEqualTo(event.getId());
    assertThat(updatedEvent.getTenantId()).isEqualTo(event.getTenantId());
    assertThat(updatedEvent.getProcessInstanceKey()).isEqualTo(event.getProcessInstanceKey());
    assertThat(updatedEvent.getFlowNodeInstanceKey()).isEqualTo(event.getFlowNodeInstanceKey());
    // new values
    assertThat(updatedEvent.getEventSourceType())
        .isEqualTo(EventSourceType.PROCESS_MESSAGE_SUBSCRIPTION);
    assertThat(updatedEvent.getDateTime()).isNotNull();
    assertThat(updatedEvent.getEventType()).isEqualTo(EventType.CREATED);
    assertThat(updatedEvent.getMetadata().getMessageName()).isEqualTo(messageName);
    assertThat(updatedEvent.getMetadata().getCorrelationKey()).isEqualTo(correlationKey);
    assertThat(updatedEvent.getPositionProcessMessageSubscription()).isEqualTo(newPosition);
  }

  @Test
  public void shouldNotOverrideEventProcessMessageFields()
      throws IOException, PersistenceException {
    // having
    // event entity with position = 2L
    final long oldPosition = 2L;
    final EventEntity event = createEvent().setPositionProcessMessageSubscription(oldPosition);
    testSearchRepository.createOrUpdateDocumentFromObject(
        eventTemplate.getFullQualifiedName(), event.getId(), event);

    // when
    // importing Zeebe record with smaller position
    final long newPosition = 1L;
    final Record<ProcessMessageSubscriptionRecordValue> zeebeRecord =
        createProcessMessageSubscriptionZeebeRecord(
            b -> b.withPosition(newPosition).withIntent(ProcessMessageSubscriptionIntent.CREATED),
            b ->
                b.withProcessInstanceKey(event.getProcessInstanceKey())
                    .withElementInstanceKey(event.getFlowNodeInstanceKey())
                    .withMessageName(messageName)
                    .withCorrelationKey(correlationKey));
    importProcessMessageSubscriptionZeebeRecord(zeebeRecord);

    // then
    // event fields are updated
    final EventEntity updatedEvent = findEventById(event.getId());
    // old values
    assertThat(updatedEvent.getId()).isEqualTo(event.getId());
    assertThat(updatedEvent.getTenantId()).isEqualTo(event.getTenantId());
    assertThat(updatedEvent.getProcessInstanceKey()).isEqualTo(event.getProcessInstanceKey());
    assertThat(updatedEvent.getFlowNodeInstanceKey()).isEqualTo(event.getFlowNodeInstanceKey());
    // old values
    assertThat(updatedEvent.getEventSourceType()).isEqualTo(event.getEventSourceType());
    assertThat(updatedEvent.getDateTime()).isEqualTo(event.getDateTime());
    assertThat(updatedEvent.getEventType()).isEqualTo(event.getEventType());
    assertThat(updatedEvent.getMetadata()).isNull();
    assertThat(updatedEvent.getPositionProcessMessageSubscription()).isEqualTo(oldPosition);
  }

  @Test
  public void shouldOverrideEventProcessInstanceFields() throws IOException, PersistenceException {
    // having
    // event entity with position = 1
    final EventEntity event = createEvent(111L, 222L).setPosition(1L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        eventTemplate.getFullQualifiedName(), event.getId(), event);

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<ProcessInstanceRecordValue> zeebeRecord =
        createProcessInstanceZeebeRecord(
            b ->
                b.withKey(event.getFlowNodeInstanceKey())
                    .withPosition(newPosition)
                    .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED),
            b ->
                b.withProcessInstanceKey(event.getProcessInstanceKey())
                    .withBpmnProcessId(newBpmnProcessId)
                    .withProcessDefinitionKey(newProcessDefinitionKey));
    importProcessInstanceZeebeRecord(zeebeRecord);

    // then
    // event fields are updated
    final EventEntity updatedEvent = findEventById(event.getId());
    // old values
    assertThat(updatedEvent.getId()).isEqualTo(event.getId());
    assertThat(updatedEvent.getTenantId()).isEqualTo(event.getTenantId());
    assertThat(updatedEvent.getProcessInstanceKey()).isEqualTo(event.getProcessInstanceKey());
    assertThat(updatedEvent.getFlowNodeInstanceKey()).isEqualTo(event.getFlowNodeInstanceKey());
    assertThat(updatedEvent.getMetadata()).isNull();
    // new values
    assertThat(updatedEvent.getEventSourceType()).isEqualTo(EventSourceType.PROCESS_INSTANCE);
    assertThat(updatedEvent.getDateTime()).isNotNull();
    assertThat(updatedEvent.getEventType()).isEqualTo(EventType.ELEMENT_COMPLETED);
    assertThat(updatedEvent.getBpmnProcessId()).isEqualTo(newBpmnProcessId);
    assertThat(updatedEvent.getProcessDefinitionKey()).isEqualTo(newProcessDefinitionKey);
    assertThat(updatedEvent.getPosition()).isEqualTo(newPosition);
  }

  @Test
  public void shouldOverrideEventProcessInstanceFieldsForNullPosition()
      throws IOException, PersistenceException {
    // having
    // event entity with null position
    final EventEntity event = createEvent(111L, 222L);
    testSearchRepository.createOrUpdateDocumentFromObject(
        eventTemplate.getFullQualifiedName(), event.getId(), event);

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 2L;
    final Record<ProcessInstanceRecordValue> zeebeRecord =
        createProcessInstanceZeebeRecord(
            b ->
                b.withKey(event.getFlowNodeInstanceKey())
                    .withPosition(newPosition)
                    .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED),
            b ->
                b.withProcessInstanceKey(event.getProcessInstanceKey())
                    .withBpmnProcessId(newBpmnProcessId)
                    .withProcessDefinitionKey(newProcessDefinitionKey));
    importProcessInstanceZeebeRecord(zeebeRecord);

    // then
    // event fields are updated
    final EventEntity updatedEvent = findEventById(event.getId());
    // old values
    assertThat(updatedEvent.getId()).isEqualTo(event.getId());
    assertThat(updatedEvent.getTenantId()).isEqualTo(event.getTenantId());
    assertThat(updatedEvent.getProcessInstanceKey()).isEqualTo(event.getProcessInstanceKey());
    assertThat(updatedEvent.getFlowNodeInstanceKey()).isEqualTo(event.getFlowNodeInstanceKey());
    assertThat(updatedEvent.getMetadata()).isNull();
    // new values
    assertThat(updatedEvent.getEventSourceType()).isEqualTo(EventSourceType.PROCESS_INSTANCE);
    assertThat(updatedEvent.getDateTime()).isNotNull();
    assertThat(updatedEvent.getEventType()).isEqualTo(EventType.ELEMENT_COMPLETED);
    assertThat(updatedEvent.getBpmnProcessId()).isEqualTo(newBpmnProcessId);
    assertThat(updatedEvent.getProcessDefinitionKey()).isEqualTo(newProcessDefinitionKey);
    assertThat(updatedEvent.getPosition()).isEqualTo(newPosition);
  }

  @Test
  public void shouldNotOverrideEventProcessInstanceFields()
      throws IOException, PersistenceException {
    // having
    // event entity with position = 2L
    final long oldPosition = 2L;
    final EventEntity event = createEvent(111L, 222L).setPosition(oldPosition);
    testSearchRepository.createOrUpdateDocumentFromObject(
        eventTemplate.getFullQualifiedName(), event.getId(), event);

    // when
    // importing Zeebe record with bigger position
    final long newPosition = 1L;
    final Record<ProcessInstanceRecordValue> zeebeRecord =
        createProcessInstanceZeebeRecord(
            b ->
                b.withKey(event.getFlowNodeInstanceKey())
                    .withPosition(newPosition)
                    .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED),
            b ->
                b.withProcessInstanceKey(event.getProcessInstanceKey())
                    .withBpmnProcessId(newBpmnProcessId)
                    .withProcessDefinitionKey(newProcessDefinitionKey));
    importProcessInstanceZeebeRecord(zeebeRecord);

    // then
    // event fields are updated
    final EventEntity updatedEvent = findEventById(event.getId());
    // old values
    assertThat(updatedEvent.getId()).isEqualTo(event.getId());
    assertThat(updatedEvent.getTenantId()).isEqualTo(event.getTenantId());
    assertThat(updatedEvent.getProcessInstanceKey()).isEqualTo(event.getProcessInstanceKey());
    assertThat(updatedEvent.getFlowNodeInstanceKey()).isEqualTo(event.getFlowNodeInstanceKey());
    assertThat(updatedEvent.getMetadata()).isNull();
    // old values
    assertThat(updatedEvent.getEventSourceType()).isEqualTo(event.getEventSourceType());
    assertThat(updatedEvent.getDateTime()).isEqualTo(event.getDateTime());
    assertThat(updatedEvent.getEventType()).isEqualTo(event.getEventType());
    assertThat(updatedEvent.getBpmnProcessId()).isEqualTo(event.getBpmnProcessId());
    assertThat(updatedEvent.getProcessDefinitionKey()).isEqualTo(event.getProcessDefinitionKey());
    assertThat(updatedEvent.getPosition()).isEqualTo(oldPosition);
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

  private void importIncidentZeebeRecord(final Record<IncidentRecordValue> zeebeRecord)
      throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    eventZeebeRecordProcessor.processIncidentRecords(
        Map.of(zeebeRecord.getValue().getElementInstanceKey(), List.of(zeebeRecord)), batchRequest);
    batchRequest.execute();
    searchContainerManager.refreshIndices(eventTemplate.getFullQualifiedName());
  }

  private void importJobZeebeRecord(final Record<JobRecordValue> zeebeRecord)
      throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    eventZeebeRecordProcessor.processJobRecords(
        Map.of(zeebeRecord.getValue().getElementInstanceKey(), List.of(zeebeRecord)), batchRequest);
    batchRequest.execute();
    searchContainerManager.refreshIndices(eventTemplate.getFullQualifiedName());
  }

  private void importProcessMessageSubscriptionZeebeRecord(
      final Record<ProcessMessageSubscriptionRecordValue> zeebeRecord) throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    eventZeebeRecordProcessor.processProcessMessageSubscription(
        Map.of(zeebeRecord.getValue().getElementInstanceKey(), List.of(zeebeRecord)), batchRequest);
    batchRequest.execute();
    searchContainerManager.refreshIndices(eventTemplate.getFullQualifiedName());
  }

  private void importProcessInstanceZeebeRecord(
      final Record<ProcessInstanceRecordValue> zeebeRecord) throws PersistenceException {
    final BatchRequest batchRequest = beanFactory.getBean(BatchRequest.class);
    eventZeebeRecordProcessor.processProcessInstanceRecords(
        Map.of(zeebeRecord.getKey(), List.of(zeebeRecord)), batchRequest);
    batchRequest.execute();
    searchContainerManager.refreshIndices(eventTemplate.getFullQualifiedName());
  }
}
