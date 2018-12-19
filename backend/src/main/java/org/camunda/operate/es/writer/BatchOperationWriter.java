/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.es.writer;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.schema.templates.WorkflowInstanceTemplate;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.dto.WorkflowInstanceBatchOperationDto;
import org.camunda.operate.rest.dto.WorkflowInstanceRequestDto;
import org.camunda.operate.rest.exception.InvalidRequestException;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class BatchOperationWriter {

  private static final Logger logger = LoggerFactory.getLogger(BatchOperationWriter.class);

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private TransportClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private WorkflowInstanceTemplate workflowInstanceTemplate;

  /**
   * Finds operation, which are scheduled or locked with expired timeout, in the amount of configured batch size, and locks them.
   * @return map with workflow instance id as a key and list of locked operations as a value
   * @throws PersistenceException
   */
  public Map<String, List<OperationEntity>> lockBatch() throws PersistenceException {
    Map<String, List<OperationEntity>> result = new HashMap<>();

    final String workerId = operateProperties.getOperationExecutor().getWorkerId();
    final long lockTimeout = operateProperties.getOperationExecutor().getLockTimeout();
    final int batchSize = operateProperties.getOperationExecutor().getBatchSize();

    //select workflow instances, which has scheduled operations, or locked with expired lockExpirationTime
    final List<WorkflowInstanceEntity> workflowInstanceEntities = workflowInstanceReader.acquireOperations(batchSize);

    BulkRequestBuilder bulkRequest = esClient.prepareBulk();

    //lock the operations
    for (WorkflowInstanceEntity workflowInstanceEntity: workflowInstanceEntities) {
      //TODO lock workflow instance https://www.elastic.co/guide/en/elasticsearch/guide/current/concurrency-solutions.html#document-locking
      final List<OperationEntity> operations = workflowInstanceEntity.getOperations();
      for (OperationEntity operation: operations) {
        if (operation.getState().equals(OperationState.SCHEDULED) ||
          (operation.getState().equals(OperationState.LOCKED) && operation.getLockExpirationTime().isBefore(OffsetDateTime.now()))) {
          //lock operation: update workerId, state, lockExpirationTime
          operation.setState(OperationState.LOCKED);
          operation.setLockOwner(workerId);
          operation.setLockExpirationTime(OffsetDateTime.now().plus(lockTimeout, ChronoUnit.MILLIS));

          CollectionUtil.addToMap(result, workflowInstanceEntity.getId(), operation);
          bulkRequest.add(createUpdateByIdRequest(workflowInstanceEntity.getId(), operation, false));

        }
      }
      //TODO unlock workflow instance (???)
    }
    ElasticsearchUtil.processBulkRequest(bulkRequest, true);
    logger.debug("{} operations locked", result.entrySet().stream().mapToLong(e -> e.getValue().size()).sum());
    return result;
  }

  private UpdateRequestBuilder createUpdateByIdRequest(String workflowInstanceId, OperationEntity operation, boolean refreshImmediately) throws PersistenceException {
    try {

      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(operation), HashMap.class);

      Map<String, Object> params = new HashMap<>();
      params.put("operation", jsonMap);

      String script =
        "for (int j = 0; j < ctx._source.operations.size(); j++) {" +
          "if (ctx._source.operations[j].id == params.operation.id) {" +
            "ctx._source.operations[j].state = params.operation.state;" +
            "ctx._source.operations[j].lockOwner = params.operation.lockOwner;" +
            "ctx._source.operations[j].lockExpirationTime = params.operation.lockExpirationTime;" +
            "ctx._source.operations[j].endDate = params.operation.endDate;" +
            "ctx._source.operations[j].errorMessage = params.operation.errorMessage;" +
            "break;" +
          "}" +
        "}";

      Script updateScript = new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, params);
      UpdateRequestBuilder updateRequestBuilder = esClient
        .prepareUpdate(workflowInstanceTemplate.getMainIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, workflowInstanceId)
        .setScript(updateScript);
      if (refreshImmediately) {
        updateRequestBuilder = updateRequestBuilder.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
      }
      return updateRequestBuilder;
    } catch (IOException e) {
      logger.error("Error preparing the query to update operation", e);
      throw new PersistenceException(String.format("Error preparing the query to update operation [%s] for workflow instance id [%s]",
        operation.getId(), workflowInstanceId), e);
    }

  }

  public UpdateRequestBuilder createOperationCompletedRequest(String workflowInstanceId, OperationType operationType) throws PersistenceException {
    try {

      Map<String, Object> params = new HashMap<>();
      params.put("type", operationType.toString());
      params.put("stateSent", OperationState.SENT.toString());
      params.put("stateLocked", OperationState.LOCKED.toString());
      params.put("stateCompleted", OperationState.COMPLETED.toString());
      params.put("endDate", OffsetDateTime.now());

      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(params), HashMap.class);

      String script =
        "for (int j = 0; j < ctx._source.operations.size(); j++) {" +
          "if (ctx._source.operations[j].type == params.type" +
            "&& (ctx._source.operations[j].state == params.stateSent || " +
                "ctx._source.operations[j].state == params.stateLocked)) {" +
            "ctx._source.operations[j].state = params.stateCompleted;" +
            "ctx._source.operations[j].endDate = params.endDate;" +
            "ctx._source.operations[j].lockOwner = null;" +
            "ctx._source.operations[j].lockExpirationTime = null;" +
            "break;" +
          "}" +
        "}";

      Script updateScript = new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, jsonMap);
      return esClient
        .prepareUpdate(workflowInstanceTemplate.getMainIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, workflowInstanceId)
        .setScript(updateScript);
    } catch (IOException e) {
      logger.error("Error preparing the query to complete operation", e);
      throw new PersistenceException(String.format("Error preparing the query to complete operation of type [%s] for workflow instance id [%s]",
        operationType, workflowInstanceId), e);
    }

  }

  public void updateOperation(String workflowInstanceId, OperationEntity operation) throws PersistenceException {
    final UpdateRequestBuilder updateRequest = createUpdateByIdRequest(workflowInstanceId, operation, true);
    ElasticsearchUtil.executeUpdate(updateRequest);
  }

  /**
   * Creates Operation objects and persists them within corresponding workflow instances.
   * @param batchOperationRequest
   * @throws PersistenceException
   */
  public void scheduleBatchOperation(WorkflowInstanceBatchOperationDto batchOperationRequest) throws PersistenceException {

    final int batchSize = operateProperties.getElasticsearch().getBatchSize();

    final SearchRequestBuilder searchRequest = workflowInstanceReader.createSearchRequest(new WorkflowInstanceRequestDto(batchOperationRequest.getQueries()));
    TimeValue keepAlive = new TimeValue(60000);

    SearchResponse scrollResp = searchRequest.setScroll(keepAlive).setSize(batchSize).get();

    SearchHits hits = scrollResp.getHits();
    validateTotalHits(hits);

    do {
      persistOperations(hits, batchOperationRequest);

      scrollResp = esClient.prepareSearchScroll(scrollResp.getScrollId()).setScroll(keepAlive).get();
      hits = scrollResp.getHits();

    } while (hits.getHits().length != 0);

  }

  private void persistOperations(SearchHits hits, WorkflowInstanceBatchOperationDto batchOperationRequest) throws PersistenceException {
    //create bulk query to persist operations for workflow instances
    logger.debug("Persisting [{}] operations to Elasticsearch", hits.getHits().length);
    BulkRequestBuilder bulkRequest = esClient.prepareBulk();
    for (SearchHit searchHit : hits.getHits()) {
      final WorkflowInstanceEntity workflowInstanceEntity = ElasticsearchUtil.fromSearchHit(searchHit.getSourceAsString(), objectMapper,WorkflowInstanceEntity.class);
      bulkRequest.add(createRequestToAddOperation(workflowInstanceEntity, batchOperationRequest));
    }
    ElasticsearchUtil.processBulkRequest(bulkRequest);
  }

  private UpdateRequestBuilder createRequestToAddOperation(WorkflowInstanceEntity entity, WorkflowInstanceBatchOperationDto batchOperationRequest)
    throws PersistenceException {
    OperationEntity operationEntity = new OperationEntity();
    operationEntity.generateId();
    operationEntity.setType(batchOperationRequest.getOperationType());
    operationEntity.setStartDate(OffsetDateTime.now());
    operationEntity.setState(OperationState.SCHEDULED);

    try {
      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(operationEntity), HashMap.class);
      Map<String, Object> params = new HashMap<>();
      params.put("operation", jsonMap);

      String script = "ctx._source.operations.add(params.operation);";

      Script updateScript = new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, params);
      return esClient
        .prepareUpdate(workflowInstanceTemplate.getMainIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
        .setScript(updateScript);
    } catch (IOException e) {
      logger.error("Error preparing the query to insert operation", e);
      throw new PersistenceException(String.format("Error preparing the query to insert operation [%s] for workflow instance id [%s]",
        batchOperationRequest.getOperationType(), entity.getId()), e);
    }

  }

  private void validateTotalHits(SearchHits hits) {
    final long totalHits = hits.getTotalHits();
    if (operateProperties.getBatchOperationMaxSize() != null &&
      totalHits > operateProperties.getBatchOperationMaxSize()) {
      throw new InvalidRequestException(String
        .format("Too many workflow instances are selected for batch operation. Maximum possible amount: %s", operateProperties.getBatchOperationMaxSize()));
    }
  }

}
