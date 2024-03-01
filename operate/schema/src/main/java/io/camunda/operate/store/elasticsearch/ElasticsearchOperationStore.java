/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.store.elasticsearch;

import static io.camunda.operate.util.ElasticsearchUtil.*;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.scroll;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.*;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.store.OperationStore;
import io.camunda.operate.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchOperationStore implements OperationStore {

  private static final Logger logger = LoggerFactory.getLogger(ElasticsearchOperationStore.class);
  @Autowired private ObjectMapper objectMapper;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private OperationTemplate operationTemplate;

  @Autowired private BatchOperationTemplate batchOperationTemplate;

  @Autowired private BeanFactory beanFactory;

  @Override
  public Map<String, String> getIndexNameForAliasAndIds(String alias, Collection<String> ids) {
    return ElasticsearchUtil.getIndexNames(alias, ids, esClient);
  }

  @Override
  public List<OperationEntity> getOperationsFor(
      Long zeebeCommandKey,
      Long processInstanceKey,
      Long incidentKey,
      OperationType operationType) {
    if (processInstanceKey == null && zeebeCommandKey == null) {
      throw new OperateRuntimeException(
          "Wrong call to search for operation. Not enough parameters.");
    }
    TermQueryBuilder zeebeCommandKeyQ =
        zeebeCommandKey != null
            ? termQuery(OperationTemplate.ZEEBE_COMMAND_KEY, zeebeCommandKey)
            : null;
    TermQueryBuilder processInstanceKeyQ =
        processInstanceKey != null
            ? termQuery(OperationTemplate.PROCESS_INSTANCE_KEY, processInstanceKey)
            : null;
    TermQueryBuilder incidentKeyQ =
        incidentKey != null ? termQuery(OperationTemplate.INCIDENT_KEY, incidentKey) : null;
    TermQueryBuilder operationTypeQ =
        operationType != null ? termQuery(OperationTemplate.TYPE, operationType.name()) : null;

    QueryBuilder query =
        joinWithAnd(
            zeebeCommandKeyQ,
            processInstanceKeyQ,
            incidentKeyQ,
            operationTypeQ,
            termsQuery(
                OperationTemplate.STATE, OperationState.SENT.name(), OperationState.LOCKED.name()));
    final SearchRequest searchRequest =
        new SearchRequest(operationTemplate.getAlias())
            .source(new SearchSourceBuilder().query(query).size(1));
    try {
      return scroll(searchRequest, OperationEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the operations: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public String add(BatchOperationEntity batchOperationEntity) throws PersistenceException {
    try {
      var indexRequest =
          new IndexRequest(batchOperationTemplate.getFullQualifiedName())
              .id(batchOperationEntity.getId())
              .source(objectMapper.writeValueAsString(batchOperationEntity), XContentType.JSON);
      esClient.index(indexRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      logger.error("Error persisting batch operation", e);
      throw new PersistenceException(
          String.format(
              "Error persisting batch operation of type [%s]", batchOperationEntity.getType()),
          e);
    }
    return batchOperationEntity.getId();
  }

  @Override
  public void update(OperationEntity operation, boolean refreshImmediately)
      throws PersistenceException {
    try {
      Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(operation), HashMap.class);

      UpdateRequest updateRequest =
          new UpdateRequest()
              .index(operationTemplate.getFullQualifiedName())
              .id(operation.getId())
              .doc(jsonMap)
              .retryOnConflict(UPDATE_RETRY_COUNT);
      if (refreshImmediately) {
        updateRequest = updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
      }
      esClient.update(updateRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to update operation [%s] for process instance id [%s]",
              operation.getId(), operation.getProcessInstanceKey()),
          e);
    }
  }

  @Override
  public void updateWithScript(
      String index, String id, String script, Map<String, Object> parameters) {
    try {
      final UpdateRequest updateRequest =
          new UpdateRequest()
              .index(index)
              .id(id)
              .script(getScriptWithParameters(script, parameters))
              .retryOnConflict(UPDATE_RETRY_COUNT);
      esClient.update(updateRequest, RequestOptions.DEFAULT);
    } catch (Exception e) {
      final String message =
          String.format("Exception occurred, while executing update request: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public BatchRequest newBatchRequest() {
    return beanFactory.getBean(BatchRequest.class);
  }

  private Script getScriptWithParameters(String script, Map<String, Object> parameters)
      throws PersistenceException {
    try {
      return new Script(
          ScriptType.INLINE,
          Script.DEFAULT_SCRIPT_LANG,
          script,
          objectMapper.readValue(objectMapper.writeValueAsString(parameters), HashMap.class));
    } catch (IOException e) {
      throw new PersistenceException(e);
    }
  }
}
