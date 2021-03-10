/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import static org.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.camunda.operate.schema.templates.BatchOperationTemplate;
import org.camunda.operate.exceptions.PersistenceException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
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
  private ObjectMapper objectMapper;

  @Autowired
  private RestHighLevelClient esClient;

  public void updateFinishedInBatchOperation(String batchOperationId) throws PersistenceException {
    this.updateFinishedInBatchOperation(batchOperationId, null);
  }

  public void updateFinishedInBatchOperation(String batchOperationId, BulkRequest bulkRequest) throws PersistenceException {
    UpdateRequest updateRequest = new UpdateRequest(batchOperationTemplate.getFullQualifiedName(), ElasticsearchUtil.ES_INDEX_TYPE, batchOperationId)
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
      logger.error("Error preparing the query to update batch operation", e);
      throw new PersistenceException("Error preparing the query to insert workflow", e);
    }

  }


}
