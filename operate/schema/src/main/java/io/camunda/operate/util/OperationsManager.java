/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.util;

import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.OperationStore;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Common methods to deal with operations, that can be used by different modules. */
@Component
public class OperationsManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(OperationsManager.class);

  @Autowired BeanFactory beanFactory;
  @Autowired private BatchOperationTemplate batchOperationTemplate;
  @Autowired private OperationTemplate operationTemplate;
  @Autowired private OperationStore operationStore;

  public void updateFinishedInBatchOperation(final String batchOperationId)
      throws PersistenceException {
    updateFinishedInBatchOperation(batchOperationId, null);
  }

  public void updateFinishedInBatchOperation(
      final String batchOperationId, final BatchRequest batchRequest) throws PersistenceException {
    final Map<String, String> ids2indexNames =
        getIndexNameForAliasAndId(batchOperationTemplate.getAlias(), batchOperationId);
    final String index = ids2indexNames.get(batchOperationId);
    if (isIndexEmptyFor(index, batchOperationId)) {
      return;
    }
    final String script =
        "ctx._source."
            + BatchOperationTemplate.OPERATIONS_FINISHED_COUNT
            + " += 1;"
            + "if (ctx._source."
            + BatchOperationTemplate.OPERATIONS_FINISHED_COUNT
            + " == ctx._source."
            + BatchOperationTemplate.OPERATIONS_TOTAL_COUNT
            + ") "
            + "   ctx._source."
            + BatchOperationTemplate.END_DATE
            + " = params.now;";
    final Map<String, Object> parameters = Map.of("now", OffsetDateTime.now());
    if (batchRequest == null) {
      operationStore.updateWithScript(index, batchOperationId, script, parameters);
    } else {
      batchRequest.updateWithScript(index, batchOperationId, script, parameters);
    }
  }

  public void updateInstancesInBatchOperation(final String batchOperationId, final long increment)
      throws PersistenceException {
    updateInstancesInBatchOperation(batchOperationId, null, increment);
  }

  public void updateInstancesInBatchOperation(
      final String batchOperationId, final BatchRequest batchRequest, final long increment)
      throws PersistenceException {
    final Map<String, String> ids2indexNames =
        getIndexNameForAliasAndId(batchOperationTemplate.getAlias(), batchOperationId);
    final String index = ids2indexNames.get(batchOperationId);
    if (isIndexEmptyFor(index, batchOperationId)) {
      return;
    }
    final String script =
        String.format("ctx._source.%s += %d;", BatchOperationTemplate.INSTANCES_COUNT, increment);
    final Map<String, Object> parameters = Map.of();
    if (batchRequest == null) {
      operationStore.updateWithScript(index, batchOperationId, script, parameters);
    } else {
      batchRequest.updateWithScript(index, batchOperationId, script, parameters);
    }
  }

  public void completeOperation(
      final Long zeebeCommandKey,
      final Long processInstanceKey,
      final Long incidentKey,
      final OperationType operationType,
      final BatchRequest batchRequest)
      throws PersistenceException {
    final BatchRequest theBatchRequest =
        Objects.requireNonNullElseGet(batchRequest, this::newBatchRequest);
    final List<OperationEntity> operationEntities =
        getOperations(zeebeCommandKey, processInstanceKey, incidentKey, operationType);
    final List<String> operationIds =
        operationEntities.stream().map(OperateEntity::getId).collect(Collectors.toList());
    final Map<String, String> ids2indexNames =
        getIndexNameForAliasAndIds(operationTemplate.getAlias(), operationIds);
    for (final OperationEntity o : operationEntities) {
      if (o.getBatchOperationId() != null) {
        updateFinishedInBatchOperation(o.getBatchOperationId(), theBatchRequest);
      }
      completeOperation(ids2indexNames.get(o.getId()), o.getId(), theBatchRequest);
    }
    if (batchRequest == null) {
      theBatchRequest.execute();
    }
  }

  public void completeOperation(final OperationEntity operationEntity) throws PersistenceException {
    completeOperation(operationEntity, true);
  }

  public void completeOperation(
      final OperationEntity operationEntity, final boolean updateFinishedInBatch)
      throws PersistenceException {
    final BatchRequest batchRequest = newBatchRequest();
    if (operationEntity.getBatchOperationId() != null && updateFinishedInBatch) {
      updateFinishedInBatchOperation(operationEntity.getBatchOperationId(), batchRequest);
    }
    final Map<String, String> ids2indexNames =
        getIndexNameForAliasAndId(operationTemplate.getAlias(), operationEntity.getId());
    completeOperation(
        ids2indexNames.get(operationEntity.getId()), operationEntity.getId(), batchRequest);
    batchRequest.execute();
  }

  private BatchRequest newBatchRequest() {
    return beanFactory.getBean(BatchRequest.class);
  }

  private List<OperationEntity> getOperations(
      final Long zeebeCommandKey,
      final Long processInstanceKey,
      final Long incidentKey,
      final OperationType operationType) {
    return operationStore.getOperationsFor(
        zeebeCommandKey, processInstanceKey, incidentKey, operationType);
  }

  private void completeOperation(
      final String indexName, final String operationId, final BatchRequest batchRequest)
      throws PersistenceException {
    final String script =
        "ctx._source.state = '"
            + OperationState.COMPLETED
            + "';"
            + "ctx._source.lockOwner = null;"
            + "ctx._source.lockExpirationTime = null;";
    batchRequest.updateWithScript(indexName, operationId, script, Map.of());
  }

  private Map<String, String> getIndexNameForAliasAndId(final String alias, final String id) {
    return getIndexNameForAliasAndIds(alias, List.of(id));
  }

  private Map<String, String> getIndexNameForAliasAndIds(
      final String alias, final Collection<String> ids) {
    return operationStore.getIndexNameForAliasAndIds(alias, ids);
  }

  private boolean isIndexEmptyFor(final String index, final String batchOperationId) {
    if (index == null || index.isEmpty()) {
      LOGGER.warn(
          "No index found for batchOperationId={}. Skip adding an update request.",
          batchOperationId);
      return true;
    }
    return false;
  }
}
