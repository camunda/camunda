/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.writer;

import static io.camunda.operate.util.CollectionUtil.getOrDefaultForNullValue;
import static io.camunda.operate.util.ConversionUtils.toLongOrNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.ListViewStore;
import io.camunda.operate.store.OperationStore;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.UserService;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Helper class that creates the individual OperationEntity objects from a batch request or
 * operation modify request. A single request might contain multiple modifications or have a query
 * for multiple process instances. An OperationEntity object is created for each process instance to
 * be modified and all the modifications are serialized into a single field.
 */
@Component
public class PersistOperationHelper {
  private final OperationStore operationStore;
  private final IncidentReader incidentReader;
  private final ListViewStore listViewStore;
  private final OperationTemplate operationTemplate;
  private final ObjectMapper objectMapper;
  private final ListViewTemplate listViewTemplate;
  private final UserService userService;

  public PersistOperationHelper(
      final OperationStore operationStore,
      final ListViewStore listViewStore,
      final OperationTemplate operationTemplate,
      final ListViewTemplate listViewTemplate,
      final IncidentReader incidentReader,
      final UserService userService,
      @Qualifier("operateObjectMapper") final ObjectMapper objectMapper) {
    this.operationStore = operationStore;
    this.incidentReader = incidentReader;
    this.listViewStore = listViewStore;
    this.operationTemplate = operationTemplate;
    this.objectMapper = objectMapper;
    this.listViewTemplate = listViewTemplate;
    this.userService = userService;
  }

  public int persistOperations(
      final List<ProcessInstanceSource> processInstanceSources,
      final String batchOperationId,
      final CreateBatchOperationRequestDto batchOperationRequest,
      final String incidentId)
      throws PersistenceException {
    final var batchRequest = operationStore.newBatchRequest();
    int operationsCount = 0;
    final OperationType operationType = batchOperationRequest.getOperationType();

    final List<Long> processInstanceKeys =
        processInstanceSources.stream()
            .map(ProcessInstanceSource::getProcessInstanceKey)
            .collect(Collectors.toList());
    Map<Long, List<Long>> incidentKeys = new HashMap<>();
    // prepare map of incident ids per process instance id
    if (operationType.equals(OperationType.RESOLVE_INCIDENT) && incidentId == null) {
      incidentKeys = incidentReader.getIncidentKeysPerProcessInstance(processInstanceKeys);
    }
    final Map<Long, String> processInstanceIdToIndexName;
    try {
      processInstanceIdToIndexName =
          listViewStore.getListViewIndicesForProcessInstances(processInstanceKeys);
    } catch (final IOException e) {
      throw new NotFoundException("Couldn't find index names for process instances.", e);
    }
    for (final ProcessInstanceSource processInstanceSource : processInstanceSources) {
      // Create each entity object and set the appropriate fields based on the operation type
      final Long processInstanceKey = processInstanceSource.getProcessInstanceKey();
      if (operationType.equals(OperationType.RESOLVE_INCIDENT) && incidentId == null) {
        final List<Long> allIncidentKeys = incidentKeys.get(processInstanceKey);
        if (allIncidentKeys != null && !allIncidentKeys.isEmpty()) {
          for (final Long incidentKey : allIncidentKeys) {
            final OperationEntity operationEntity =
                createOperationEntity(processInstanceSource, operationType, batchOperationId)
                    .setIncidentKey(incidentKey);
            batchRequest.add(operationTemplate.getFullQualifiedName(), operationEntity);
            operationsCount++;
          }
        }
      } else {
        final OperationEntity operationEntity =
            createOperationEntity(processInstanceSource, operationType, batchOperationId)
                .setIncidentKey(toLongOrNull(incidentId));
        if (operationType == OperationType.MIGRATE_PROCESS_INSTANCE) {
          try {
            operationEntity.setMigrationPlan(
                objectMapper.writeValueAsString(batchOperationRequest.getMigrationPlan()));
          } catch (final IOException e) {
            throw new PersistenceException(e);
          }
        } else if (operationType == OperationType.MODIFY_PROCESS_INSTANCE) {
          try {
            final ModifyProcessInstanceRequestDto modOp =
                new ModifyProcessInstanceRequestDto()
                    .setProcessInstanceKey(
                        String.valueOf(processInstanceSource.getProcessInstanceKey()))
                    .setModifications(batchOperationRequest.getModifications());
            operationEntity.setModifyInstructions(objectMapper.writeValueAsString(modOp));
          } catch (final IOException e) {
            throw new PersistenceException(e);
          }
        }
        batchRequest.add(operationTemplate.getFullQualifiedName(), operationEntity);
        operationsCount++;
      }

      // Place the update script in the batch request
      final String processInstanceId = String.valueOf(processInstanceKey);
      final String indexForProcessInstance =
          getOrDefaultForNullValue(
              processInstanceIdToIndexName,
              processInstanceKey,
              listViewTemplate.getFullQualifiedName());
      final Map<String, Object> params = Map.of("batchOperationId", batchOperationId);
      final String script =
          "if (ctx._source.batchOperationIds == null){"
              + "ctx._source.batchOperationIds = new String[]{params.batchOperationId};"
              + "} else {"
              + "ctx._source.batchOperationIds.add(params.batchOperationId);"
              + "}";
      batchRequest.updateWithScript(indexForProcessInstance, processInstanceId, script, params);
    }

    batchRequest.execute();
    return operationsCount;
  }

  private OperationEntity createOperationEntity(
      final ProcessInstanceSource processInstanceSource,
      final OperationType operationType,
      final String batchOperationId) {

    final OperationEntity operationEntity =
        new OperationEntity()
            .withGeneratedId()
            .setProcessInstanceKey(processInstanceSource.getProcessInstanceKey())
            .setProcessDefinitionKey(processInstanceSource.getProcessDefinitionKey())
            .setBpmnProcessId(processInstanceSource.getBpmnProcessId())
            .setType(operationType)
            .setState(OperationState.SCHEDULED)
            .setBatchOperationId(batchOperationId)
            .setUsername(userService.getCurrentUser().getUsername());

    return operationEntity;
  }
}
