/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import static io.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.scroll;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.OperationTemplate;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.exceptions.PersistenceException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Common methods to deal with operations, that can be used by different modules.
 */
@Component
public class OperationsManager {

  private static final Logger logger = LoggerFactory.getLogger(OperationsManager.class);

  @Autowired
  private BatchOperationTemplate batchOperationTemplate;

  @Autowired
  private OperationTemplate operationTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private OperateProperties operateProperties;

  public void updateFinishedInBatchOperation(String batchOperationId) throws PersistenceException {
    this.updateFinishedInBatchOperation(batchOperationId, null);
  }

  public void updateFinishedInBatchOperation(final String batchOperationId, final BulkRequest bulkRequest) throws PersistenceException {
    final Map<String,String> ids2indexNames = getIndexNameForAliasAndId(batchOperationTemplate.getAlias(), batchOperationId);
    final UpdateRequest updateRequest = new UpdateRequest()
            .index(ids2indexNames.get(batchOperationId))
            .id(batchOperationId)
            .script(getIncrementFinishedScript())
            .retryOnConflict(UPDATE_RETRY_COUNT);
    if (bulkRequest == null) {
      ElasticsearchUtil.executeUpdate(esClient, updateRequest);
    } else {
      bulkRequest.add(updateRequest);
    }
  }

  private Script getIncrementFinishedScript() throws PersistenceException {
    try {
      Map<String,Object> paramsMap = new HashMap<>();
      paramsMap.put("now", OffsetDateTime.now());
      Map<String, Object> jsonMap = objectMapper.readValue(objectMapper.writeValueAsString(paramsMap), HashMap.class);
      String script = "ctx._source." + BatchOperationTemplate.OPERATIONS_FINISHED_COUNT + " += 1;"
          + "if (ctx._source." + BatchOperationTemplate.OPERATIONS_FINISHED_COUNT + " == ctx._source." + BatchOperationTemplate.OPERATIONS_TOTAL_COUNT + ") "
          + "   ctx._source." + BatchOperationTemplate.END_DATE + " = params.now;";
      return new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, jsonMap);
    } catch (IOException e) {
      throw new PersistenceException("Error preparing the query to update batch operation", e);
    }

  }

  public void completeOperation(final Long zeebeCommandKey, final Long processInstanceKey, final Long incidentKey,
                                final OperationType operationType, final BulkRequest bulkRequest)
      throws PersistenceException {
    final BulkRequest theBulkRequest = Objects.requireNonNullElseGet(bulkRequest, BulkRequest::new);
    final List<OperationEntity> operationEntities = getOperations(zeebeCommandKey, processInstanceKey, incidentKey, operationType);
    final List<String> operationIds = operationEntities.stream().map(OperateEntity::getId).collect(Collectors.toList());
    final Map<String,String> ids2indexNames = getIndexNameForAliasAndIds(operationTemplate.getAlias(), operationIds);
    for (final OperationEntity o: operationEntities) {
      if (o.getBatchOperationId() != null) {
        updateFinishedInBatchOperation(o.getBatchOperationId(), theBulkRequest);
      }
      completeOperation(ids2indexNames.get(o.getId()),o.getId(), theBulkRequest);
    }
    if (bulkRequest == null) {
      ElasticsearchUtil.processBulkRequest(esClient, theBulkRequest, operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());
    }
  }

  public void completeOperation(final OperationEntity operationEntity) throws PersistenceException {
    final BulkRequest bulkRequest = new BulkRequest();
    if (operationEntity.getBatchOperationId() != null) {
        updateFinishedInBatchOperation(operationEntity.getBatchOperationId(), bulkRequest);
    }
    final Map<String,String> ids2indexNames =  getIndexNameForAliasAndId(operationTemplate.getAlias(), operationEntity.getId());
    completeOperation(ids2indexNames.get(operationEntity.getId()), operationEntity.getId(), bulkRequest);
    ElasticsearchUtil.processBulkRequest(esClient, bulkRequest, operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());
  }

  private List<OperationEntity> getOperations(Long zeebeCommandKey, Long processInstanceKey, Long incidentKey, OperationType operationType){
    if (processInstanceKey == null && zeebeCommandKey == null) {
      throw new OperateRuntimeException("Wrong call to search for operation. Not enough parameters.");
    }
    TermQueryBuilder zeebeCommandKeyQ = zeebeCommandKey != null ? termQuery(OperationTemplate.ZEEBE_COMMAND_KEY, zeebeCommandKey) : null;
    TermQueryBuilder processInstanceKeyQ = processInstanceKey != null ? termQuery(OperationTemplate.PROCESS_INSTANCE_KEY, processInstanceKey) : null;
    TermQueryBuilder incidentKeyQ = incidentKey != null ? termQuery(OperationTemplate.INCIDENT_KEY, incidentKey) : null;
    TermQueryBuilder operationTypeQ = operationType != null ? termQuery(OperationTemplate.TYPE, operationType.name()) : null;

    QueryBuilder query =
        joinWithAnd(
            zeebeCommandKeyQ,
            processInstanceKeyQ,
            incidentKeyQ,
            operationTypeQ,
            termsQuery(OperationTemplate.STATE, OperationState.SENT.name(), OperationState.LOCKED.name())
        );
    final SearchRequest searchRequest = new SearchRequest(operationTemplate.getAlias())
        .source(new SearchSourceBuilder()
            .query(query)
            .size(1));
    try {
      return scroll(searchRequest, OperationEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining the operations: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private void completeOperation(final String indexName, final String operationId, final BulkRequest bulkRequest) {
    final UpdateRequest updateRequest = new UpdateRequest().index(indexName).id(operationId)
        .script(getUpdateOperationScript())
        .retryOnConflict(UPDATE_RETRY_COUNT);
    bulkRequest.add(updateRequest);
  }

  private Script getUpdateOperationScript() {
    final String script =
        "ctx._source.state = '" + OperationState.COMPLETED + "';" +
        "ctx._source.lockOwner = null;" +
        "ctx._source.lockExpirationTime = null;";
    return new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, Collections.emptyMap());
  }
  private Map<String,String> getIndexNameForAliasAndId(final String alias, final String id){
     return getIndexNameForAliasAndIds(alias, List.of(id));
  }
  private Map<String,String> getIndexNameForAliasAndIds(final String alias,final Collection<String> ids){
      return ElasticsearchUtil.getIndexNames(alias, ids, esClient);
  }
}
