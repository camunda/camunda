/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.utils.ExporterUtil.tenantOrDefault;
import static io.camunda.exporter.utils.ExporterUtil.toOffsetDateTime;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.EventTemplate;
import io.camunda.webapps.schema.entities.event.EventEntity;
import io.camunda.webapps.schema.entities.event.EventMetadataEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public class EventFromJobHandler extends AbstractEventHandler<JobRecordValue> {
  protected static final Set<JobIntent> JOB_EVENTS =
      Set.of(
          JobIntent.CREATED,
          JobIntent.COMPLETED,
          JobIntent.TIMED_OUT,
          JobIntent.FAILED,
          JobIntent.RETRIES_UPDATED,
          JobIntent.CANCELED,
          JobIntent.MIGRATED);

  public EventFromJobHandler(final String indexName) {
    super(indexName);
  }

  @Override
  public ValueType getHandledValueType() {
    return ValueType.JOB;
  }

  @Override
  public boolean handlesRecord(final Record<JobRecordValue> record) {
    final JobIntent intent = (JobIntent) record.getIntent();
    return JOB_EVENTS.contains(intent);
  }

  @Override
  public List<String> generateIds(final Record<JobRecordValue> record) {
    return List.of(
        String.format(
            ID_PATTERN,
            record.getValue().getProcessInstanceKey(),
            record.getValue().getElementInstanceKey()));
  }

  @Override
  public void updateEntity(final Record<JobRecordValue> record, final EventEntity entity) {

    final JobRecordValue recordValue = record.getValue();
    entity
        .setId(
            String.format(
                ID_PATTERN,
                recordValue.getProcessInstanceKey(),
                recordValue.getElementInstanceKey()))
        .setPositionJob(record.getPosition());
    loadEventGeneralData(record, entity);
    final long processDefinitionKey = recordValue.getProcessDefinitionKey();
    if (processDefinitionKey > 0) {
      entity.setProcessDefinitionKey(processDefinitionKey);
    }

    final long processInstanceKey = recordValue.getProcessInstanceKey();
    if (processInstanceKey > 0) {
      entity.setProcessInstanceKey(processInstanceKey);
    }

    entity
        .setBpmnProcessId(recordValue.getBpmnProcessId())
        .setFlowNodeId(recordValue.getElementId())
        .setTenantId(tenantOrDefault(recordValue.getTenantId()));

    final long activityInstanceKey = recordValue.getElementInstanceKey();
    if (activityInstanceKey > 0) {
      entity.setFlowNodeInstanceKey(activityInstanceKey);
    }

    final EventMetadataEntity eventMetadata = populateEventMetadata(record, recordValue);

    entity.setMetadata(eventMetadata);
  }

  @Override
  public void flush(final EventEntity entity, final BatchRequest batchRequest) {
    persistEvent(entity, EventTemplate.POSITION_JOB, entity.getPositionJob(), batchRequest);
  }

  private EventMetadataEntity populateEventMetadata(
      final Record<JobRecordValue> record, final JobRecordValue recordValue) {
    final EventMetadataEntity eventMetadata = new EventMetadataEntity();
    eventMetadata.setJobType(recordValue.getType());
    eventMetadata.setJobRetries(recordValue.getRetries());
    eventMetadata.setJobWorker(recordValue.getWorker());
    eventMetadata.setJobCustomHeaders(recordValue.getCustomHeaders());

    if (record.getKey() > 0) {
      eventMetadata.setJobKey(record.getKey());
    }

    final long jobDeadline = recordValue.getDeadline();
    if (jobDeadline >= 0) {
      eventMetadata.setJobDeadline(toOffsetDateTime(Instant.ofEpochMilli(jobDeadline)));
    }
    return eventMetadata;
  }
}
