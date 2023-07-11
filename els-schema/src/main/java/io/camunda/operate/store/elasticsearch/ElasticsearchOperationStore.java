/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.store.OperationStore;
import io.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.camunda.operate.util.ElasticsearchUtil.*;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component
@Profile("!opensearch")
public class ElasticsearchOperationStore implements OperationStore {

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private OperationTemplate operationTemplate;

  @Override
  public Map<String, String> getIndexNameForAliasAndIds(String alias, Collection<String> ids) {
    return ElasticsearchUtil.getIndexNames(alias, ids, esClient);
  }

  @Override
  public List<OperationEntity> getOperationsFor(Long zeebeCommandKey, Long processInstanceKey,
      Long incidentKey, OperationType operationType) {
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

  @Override
  public void updateWithScript(String index, String id, String script,
      Map<String, Object> parameters) {
    try {
      final UpdateRequest updateRequest = new UpdateRequest()
        .index(index)
        .id(id)
        .script(getScriptWithParameters(script, parameters))
        .retryOnConflict(UPDATE_RETRY_COUNT);
      esClient.update(updateRequest, RequestOptions.DEFAULT);
    } catch (Exception e) {
      final String message = String.format("Exception occurred, while executing update request: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private Script getScriptWithParameters(String script, Map<String, Object> parameters) throws PersistenceException {
    try {
      return new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, objectMapper.readValue(objectMapper.writeValueAsString(parameters), HashMap.class));
    } catch (IOException e) {
      throw new PersistenceException(e);
    }
  }
}
