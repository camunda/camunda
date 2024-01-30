/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.schema.templates.OperationTemplate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.OperationStore;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Common methods to deal with operations, that can be used by different modules.
 */
@Component
public class OperationsManager {

  @Autowired
  private BatchOperationTemplate batchOperationTemplate;

  @Autowired
  private OperationTemplate operationTemplate;

  @Autowired
  private OperationStore operationStore;

  @Autowired
  BeanFactory beanFactory;

  public void updateFinishedInBatchOperation(String batchOperationId) throws PersistenceException {
    this.updateFinishedInBatchOperation(batchOperationId, null);
  }

  public void updateFinishedInBatchOperation(final String batchOperationId, final BatchRequest batchRequest) throws PersistenceException {
    final Map<String,String> ids2indexNames = getIndexNameForAliasAndId(batchOperationTemplate.getAlias(), batchOperationId);
    final String index = ids2indexNames.get(batchOperationId);
    final String script = "ctx._source." + BatchOperationTemplate.OPERATIONS_FINISHED_COUNT + " += 1;"
        + "if (ctx._source." + BatchOperationTemplate.OPERATIONS_FINISHED_COUNT + " == ctx._source." + BatchOperationTemplate.OPERATIONS_TOTAL_COUNT + ") "
        + "   ctx._source." + BatchOperationTemplate.END_DATE + " = params.now;";
    final Map<String,Object> parameters = Map.of("now", OffsetDateTime.now());
    if (batchRequest == null) {
      operationStore.updateWithScript(index, batchOperationId, script, parameters);
    } else {
      batchRequest.updateWithScript(index, batchOperationId, script, parameters);
    }
  }

  public void updateInstancesInBatchOperation(String batchOperationId, long increment) throws PersistenceException {
    this.updateInstancesInBatchOperation(batchOperationId, null, increment);
  }

  public void updateInstancesInBatchOperation(final String batchOperationId, final BatchRequest batchRequest, long increment) throws PersistenceException {
    final Map<String, String> ids2indexNames = getIndexNameForAliasAndId(batchOperationTemplate.getAlias(), batchOperationId);
    final String index = ids2indexNames.get(batchOperationId);
    final String script = String.format("ctx._source.%s += %d;", BatchOperationTemplate.INSTANCES_COUNT, increment);
    final Map<String, Object> parameters = Map.of();
    if (batchRequest == null) {
      operationStore.updateWithScript(index, batchOperationId, script, parameters);
    } else {
      batchRequest.updateWithScript(index, batchOperationId, script, parameters);
    }
  }

  public void completeOperation(final Long zeebeCommandKey, final Long processInstanceKey, final Long incidentKey,
                                final OperationType operationType, final BatchRequest batchRequest)
      throws PersistenceException {
    final BatchRequest theBatchRequest = Objects.requireNonNullElseGet(batchRequest, this::newBatchRequest);
    final List<OperationEntity> operationEntities = getOperations(zeebeCommandKey, processInstanceKey, incidentKey, operationType);
    final List<String> operationIds = operationEntities.stream().map(OperateEntity::getId).collect(Collectors.toList());
    final Map<String,String> ids2indexNames = getIndexNameForAliasAndIds(operationTemplate.getAlias(), operationIds);
    for (final OperationEntity o: operationEntities) {
      if (o.getBatchOperationId() != null) {
        updateFinishedInBatchOperation(o.getBatchOperationId(), theBatchRequest);
      }
      completeOperation(ids2indexNames.get(o.getId()),o.getId(), theBatchRequest);
    }
    if (batchRequest == null) {
      theBatchRequest.execute();
    }
  }

  public void completeOperation(final OperationEntity operationEntity) throws PersistenceException {
    completeOperation(operationEntity, true);
  }

  public void completeOperation(final OperationEntity operationEntity, boolean updateFinishedInBatch) throws PersistenceException {
    final BatchRequest batchRequest = newBatchRequest();
    if (operationEntity.getBatchOperationId() != null && updateFinishedInBatch) {
      updateFinishedInBatchOperation(operationEntity.getBatchOperationId(), batchRequest);
    }
    final Map<String, String> ids2indexNames = getIndexNameForAliasAndId(operationTemplate.getAlias(), operationEntity.getId());
    completeOperation(ids2indexNames.get(operationEntity.getId()), operationEntity.getId(), batchRequest);
    batchRequest.execute();
  }

  private BatchRequest newBatchRequest() {
    return beanFactory.getBean(BatchRequest.class);
  }

  private List<OperationEntity> getOperations(Long zeebeCommandKey, Long processInstanceKey, Long incidentKey, OperationType operationType){
    return operationStore.getOperationsFor(zeebeCommandKey, processInstanceKey, incidentKey, operationType);
  }

  private void completeOperation(final String indexName, final String operationId, final BatchRequest batchRequest)
      throws PersistenceException {
    final String script =
        "ctx._source.state = '" + OperationState.COMPLETED + "';" + "ctx._source.lockOwner = null;" +
            "ctx._source.lockExpirationTime = null;";
    batchRequest.updateWithScript(indexName, operationId, script, Map.of());
  }
  private Map<String,String> getIndexNameForAliasAndId(final String alias, final String id){
     return getIndexNameForAliasAndIds(alias, List.of(id));
  }
  private Map<String,String> getIndexNameForAliasAndIds(final String alias,final Collection<String> ids){
      return operationStore.getIndexNameForAliasAndIds(alias, ids);
  }
}
