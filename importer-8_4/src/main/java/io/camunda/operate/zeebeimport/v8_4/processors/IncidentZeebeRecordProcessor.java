/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.v8_4.processors;

import static io.camunda.operate.zeebeimport.util.ImportUtil.tenantOrDefault;

import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.entities.post.PostImporterActionType;
import io.camunda.operate.entities.post.PostImporterQueueEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.PostImporterQueueTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.ConversionUtils;
import io.camunda.operate.util.DateUtil;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.zeebeimport.IncidentNotifier;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class IncidentZeebeRecordProcessor {

  private static final Logger logger = LoggerFactory.getLogger(IncidentZeebeRecordProcessor.class);

  @Autowired private OperateProperties operateProperties;

  @Autowired private IncidentTemplate incidentTemplate;

  @Autowired private PostImporterQueueTemplate postImporterQueueTemplate;

  @Autowired private OperationsManager operationsManager;

  @Autowired private IncidentNotifier incidentNotifier;

  public void processIncidentRecord(final List<Record> records, final BatchRequest batchRequest)
      throws PersistenceException {
    final List<IncidentEntity> newIncidents = new ArrayList<>();
    for (final Record record : records) {
      processIncidentRecord(record, batchRequest, newIncidents::add);
    }
    if (operateProperties.getAlert().getWebhook() != null) {
      incidentNotifier.notifyOnIncidents(newIncidents);
    }
  }

  public void processIncidentRecord(
      final Record record,
      final BatchRequest batchRequest,
      final Consumer<IncidentEntity> newIncidentHandler)
      throws PersistenceException {
    final IncidentRecordValue recordValue = (IncidentRecordValue) record.getValue();

    persistIncident(record, recordValue, batchRequest, newIncidentHandler);

    persistPostImportQueueEntry(record, recordValue, batchRequest);
  }

  private void persistPostImportQueueEntry(
      final Record record, final IncidentRecordValue recordValue, final BatchRequest batchRequest)
      throws PersistenceException {
    final String intent = record.getIntent().name();
    final PostImporterQueueEntity postImporterQueueEntity =
        new PostImporterQueueEntity()
            // id = incident key + intent
            .setId(String.format("%d-%s", record.getKey(), intent))
            .setActionType(PostImporterActionType.INCIDENT)
            .setIntent(intent)
            .setKey(record.getKey())
            .setPosition(record.getPosition())
            .setCreationTime(OffsetDateTime.now())
            .setPartitionId(record.getPartitionId())
            .setProcessInstanceKey(recordValue.getProcessInstanceKey());

    batchRequest.add(postImporterQueueTemplate.getFullQualifiedName(), postImporterQueueEntity);
  }

  private void persistIncident(
      final Record record,
      final IncidentRecordValue recordValue,
      final BatchRequest batchRequest,
      final Consumer<IncidentEntity> newIncidentHandler)
      throws PersistenceException {
    final String intentStr = record.getIntent().name();
    final Long incidentKey = record.getKey();
    if (intentStr.equals(IncidentIntent.RESOLVED.toString())) {

      // resolve corresponding operation
      operationsManager.completeOperation(
          null,
          recordValue.getProcessInstanceKey(),
          incidentKey,
          OperationType.RESOLVE_INCIDENT,
          batchRequest);
      // resolved incident is not updated directly, only in post importer
    } else if (intentStr.equals(IncidentIntent.CREATED.toString())) {
      final IncidentEntity incident =
          new IncidentEntity()
              .setId(ConversionUtils.toStringOrNull(incidentKey))
              .setKey(incidentKey)
              .setPartitionId(record.getPartitionId());
      if (recordValue.getJobKey() > 0) {
        incident.setJobKey(recordValue.getJobKey());
      }
      if (recordValue.getProcessInstanceKey() > 0) {
        incident.setProcessInstanceKey(recordValue.getProcessInstanceKey());
      }
      if (recordValue.getProcessDefinitionKey() > 0) {
        incident.setProcessDefinitionKey(recordValue.getProcessDefinitionKey());
      }
      incident.setBpmnProcessId(recordValue.getBpmnProcessId());
      final String errorMessage = StringUtils.trimWhitespace(recordValue.getErrorMessage());
      incident
          .setErrorMessage(errorMessage)
          .setErrorType(
              ErrorType.fromZeebeErrorType(
                  recordValue.getErrorType() == null ? null : recordValue.getErrorType().name()))
          .setFlowNodeId(recordValue.getElementId());
      if (recordValue.getElementInstanceKey() > 0) {
        incident.setFlowNodeInstanceKey(recordValue.getElementInstanceKey());
      }
      incident
          .setState(IncidentState.PENDING)
          .setCreationTime(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())))
          .setTenantId(tenantOrDefault(recordValue.getTenantId()));

      logger.debug("Index incident: id {}", incident.getId());
      // we only insert incidents but never update -> update will be performed in post importer
      batchRequest.upsert(
          incidentTemplate.getFullQualifiedName(),
          String.valueOf(incident.getKey()),
          incident,
          Map.of());
      newIncidentHandler.accept(incident);
    }
  }
}
