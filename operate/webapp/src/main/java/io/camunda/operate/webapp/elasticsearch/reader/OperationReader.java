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
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.entities.OperationState.LOCKED;
import static io.camunda.operate.entities.OperationState.SCHEDULED;
import static io.camunda.operate.schema.templates.OperationTemplate.BATCH_OPERATION_ID;
import static io.camunda.operate.schema.templates.OperationTemplate.ID;
import static io.camunda.operate.schema.templates.OperationTemplate.INCIDENT_KEY;
import static io.camunda.operate.schema.templates.OperationTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.operate.schema.templates.OperationTemplate.SCOPE_KEY;
import static io.camunda.operate.schema.templates.OperationTemplate.TYPE;
import static io.camunda.operate.schema.templates.OperationTemplate.VARIABLE_NAME;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithOr;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import io.camunda.operate.webapp.security.UserService;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class OperationReader extends AbstractReader
    implements io.camunda.operate.webapp.reader.OperationReader {

  private static final Logger logger = LoggerFactory.getLogger(OperationReader.class);

  private static final String SCHEDULED_OPERATION = SCHEDULED.toString();
  private static final String LOCKED_OPERATION = LOCKED.toString();

  @Autowired private OperationTemplate operationTemplate;

  @Autowired private BatchOperationTemplate batchOperationTemplate;

  @Autowired private DateTimeFormatter dateTimeFormatter;

  @Autowired private UserService userService;

  /**
   * Request process instances, that have scheduled operations or locked but with expired locks.
   *
   * @param batchSize
   * @return
   */
  @Override
  public List<OperationEntity> acquireOperations(int batchSize) {
    final TermQueryBuilder scheduledOperationsQuery =
        termQuery(OperationTemplate.STATE, SCHEDULED_OPERATION);
    final TermQueryBuilder lockedOperationsQuery =
        termQuery(OperationTemplate.STATE, LOCKED_OPERATION);
    final RangeQueryBuilder lockExpirationTimeQuery =
        rangeQuery(OperationTemplate.LOCK_EXPIRATION_TIME);
    lockExpirationTimeQuery.lte(dateTimeFormatter.format(OffsetDateTime.now()));

    final QueryBuilder operationsQuery =
        joinWithOr(
            scheduledOperationsQuery, joinWithAnd(lockedOperationsQuery, lockExpirationTimeQuery));

    ConstantScoreQueryBuilder constantScoreQuery = constantScoreQuery(operationsQuery);

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(operationTemplate, ONLY_RUNTIME)
            .source(
                new SearchSourceBuilder()
                    .query(constantScoreQuery)
                    .sort(BATCH_OPERATION_ID, SortOrder.ASC)
                    .from(0)
                    .size(batchSize));
    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return ElasticsearchUtil.mapSearchHits(
          searchResponse.getHits().getHits(), objectMapper, OperationEntity.class);
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while acquiring operations for execution: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Map<Long, List<OperationEntity>> getOperationsPerProcessInstanceKey(
      List<Long> processInstanceKeys) {
    Map<Long, List<OperationEntity>> result = new HashMap<>();

    TermsQueryBuilder processInstanceKeysQ = termsQuery(PROCESS_INSTANCE_KEY, processInstanceKeys);
    final ConstantScoreQueryBuilder query =
        constantScoreQuery(joinWithAnd(processInstanceKeysQ, createUsernameQuery()));

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(operationTemplate, ElasticsearchUtil.QueryType.ALL)
            .source(
                new SearchSourceBuilder()
                    .query(query)
                    .sort(PROCESS_INSTANCE_KEY, SortOrder.ASC)
                    .sort(ID, SortOrder.ASC));

    try {
      ElasticsearchUtil.scroll(
          searchRequest,
          OperationEntity.class,
          objectMapper,
          esClient,
          hits -> {
            final List<OperationEntity> operationEntities =
                ElasticsearchUtil.mapSearchHits(
                    hits.getHits(), objectMapper, OperationEntity.class);
            for (OperationEntity operationEntity : operationEntities) {
              CollectionUtil.addToMap(
                  result, operationEntity.getProcessInstanceKey(), operationEntity);
            }
          },
          null);

      return result;
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining operations per process instance id: %s",
              e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Map<Long, List<OperationEntity>> getOperationsPerIncidentKey(String processInstanceId) {
    Map<Long, List<OperationEntity>> result = new HashMap<>();

    TermQueryBuilder processInstanceKeysQ = termQuery(PROCESS_INSTANCE_KEY, processInstanceId);
    final ConstantScoreQueryBuilder query =
        constantScoreQuery(joinWithAnd(processInstanceKeysQ, createUsernameQuery()));

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(operationTemplate, ONLY_RUNTIME)
            .source(
                new SearchSourceBuilder()
                    .query(query)
                    .sort(INCIDENT_KEY, SortOrder.ASC)
                    .sort(ID, SortOrder.ASC));
    try {
      ElasticsearchUtil.scroll(
          searchRequest,
          OperationEntity.class,
          objectMapper,
          esClient,
          hits -> {
            final List<OperationEntity> operationEntities =
                ElasticsearchUtil.mapSearchHits(
                    hits.getHits(), objectMapper, OperationEntity.class);
            for (OperationEntity operationEntity : operationEntities) {
              CollectionUtil.addToMap(result, operationEntity.getIncidentKey(), operationEntity);
            }
          },
          null);
      return result;
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining operations per incident id: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Map<String, List<OperationEntity>> getUpdateOperationsPerVariableName(
      Long processInstanceKey, Long scopeKey) {
    Map<String, List<OperationEntity>> result = new HashMap<>();

    final TermQueryBuilder processInstanceKeyQuery =
        termQuery(PROCESS_INSTANCE_KEY, processInstanceKey);
    final TermQueryBuilder scopeKeyQuery = termQuery(SCOPE_KEY, scopeKey);
    final TermQueryBuilder operationTypeQ = termQuery(TYPE, OperationType.UPDATE_VARIABLE.name());
    final ConstantScoreQueryBuilder query =
        constantScoreQuery(
            joinWithAnd(
                processInstanceKeyQuery, scopeKeyQuery, operationTypeQ, createUsernameQuery()));

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(operationTemplate, ALL)
            .source(new SearchSourceBuilder().query(query).sort(ID, SortOrder.ASC));
    try {
      ElasticsearchUtil.scroll(
          searchRequest,
          OperationEntity.class,
          objectMapper,
          esClient,
          hits -> {
            final List<OperationEntity> operationEntities =
                ElasticsearchUtil.mapSearchHits(
                    hits.getHits(), objectMapper, OperationEntity.class);
            for (OperationEntity operationEntity : operationEntities) {
              CollectionUtil.addToMap(result, operationEntity.getVariableName(), operationEntity);
            }
          },
          null);
      return result;
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining operations per variable name: %s",
              e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<OperationEntity> getOperationsByProcessInstanceKey(Long processInstanceKey) {

    TermQueryBuilder processInstanceQ =
        processInstanceKey == null ? null : termQuery(PROCESS_INSTANCE_KEY, processInstanceKey);
    QueryBuilder query = constantScoreQuery(joinWithAnd(processInstanceQ, createUsernameQuery()));
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(operationTemplate, ALL)
            .source(new SearchSourceBuilder().query(query).sort(ID, SortOrder.ASC));
    try {
      return ElasticsearchUtil.scroll(searchRequest, OperationEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining operations: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  // this query will be extended
  @Override
  public List<BatchOperationEntity> getBatchOperations(int pageSize) {
    String username = userService.getCurrentUser().getUsername();
    TermQueryBuilder isOfCurrentUser = termQuery(BatchOperationTemplate.USERNAME, username);
    SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(batchOperationTemplate, ALL)
            .source(
                new SearchSourceBuilder()
                    .query(constantScoreQuery(isOfCurrentUser))
                    .size(pageSize));
    try {
      return ElasticsearchUtil.mapSearchHits(
          esClient.search(searchRequest, RequestOptions.DEFAULT).getHits().getHits(),
          objectMapper,
          BatchOperationEntity.class);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining batch operations: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<OperationDto> getOperationsByBatchOperationId(String batchOperationId) {
    final TermQueryBuilder operationIdQ = termQuery(BATCH_OPERATION_ID, batchOperationId);
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(operationTemplate, ALL)
            .source(new SearchSourceBuilder().query(operationIdQ));
    try {
      final List<OperationEntity> operationEntities =
          ElasticsearchUtil.scroll(searchRequest, OperationEntity.class, objectMapper, esClient);
      return DtoCreator.create(operationEntities, OperationDto.class);
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while searching for operation with batchOperationId: %s",
              e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<OperationDto> getOperations(
      OperationType operationType, String processInstanceId, String scopeId, String variableName) {
    final TermQueryBuilder operationTypeQ = termQuery(TYPE, operationType);
    final TermQueryBuilder processInstanceKeyQ = termQuery(PROCESS_INSTANCE_KEY, processInstanceId);
    final TermQueryBuilder scopeKeyQ = termQuery(SCOPE_KEY, scopeId);
    final TermQueryBuilder variableNameQ = termQuery(VARIABLE_NAME, variableName);
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(operationTemplate, ALL)
            .source(
                new SearchSourceBuilder()
                    .query(
                        joinWithAnd(
                            operationTypeQ, processInstanceKeyQ, scopeKeyQ, variableNameQ)));
    try {
      final List<OperationEntity> operationEntities =
          ElasticsearchUtil.scroll(searchRequest, OperationEntity.class, objectMapper, esClient);
      return DtoCreator.create(operationEntities, OperationDto.class);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while searching for operation.", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private QueryBuilder createUsernameQuery() {
    return termQuery(OperationTemplate.USERNAME, userService.getCurrentUser().getUsername());
  }
}
