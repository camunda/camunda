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
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.entities.OperationState.LOCKED;
import static io.camunda.operate.entities.OperationState.SCHEDULED;
import static io.camunda.operate.schema.templates.OperationTemplate.BATCH_OPERATION_ID;
import static io.camunda.operate.schema.templates.OperationTemplate.ID;
import static io.camunda.operate.schema.templates.OperationTemplate.INCIDENT_KEY;
import static io.camunda.operate.schema.templates.OperationTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.operate.schema.templates.OperationTemplate.SCOPE_KEY;
import static io.camunda.operate.schema.templates.OperationTemplate.TYPE;
import static io.camunda.operate.schema.templates.OperationTemplate.VARIABLE_NAME;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.constantScore;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.longTerms;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.lte;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.or;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.sortOptions;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ALL;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static org.opensearch.client.opensearch._types.SortOrder.Asc;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import io.camunda.operate.webapp.security.UserService;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchOperationReader extends OpensearchAbstractReader implements OperationReader {

  private static final Logger logger = LoggerFactory.getLogger(OpensearchOperationReader.class);

  private static final String SCHEDULED_OPERATION = SCHEDULED.toString();
  private static final String LOCKED_OPERATION = LOCKED.toString();
  @Autowired RichOpenSearchClient richOpenSearchClient;
  @Autowired private OperationTemplate operationTemplate;
  @Autowired private BatchOperationTemplate batchOperationTemplate;
  @Autowired private DateTimeFormatter dateTimeFormatter;
  @Autowired private UserService userService;

  private Query usernameQuery() {
    return term(OperationTemplate.USERNAME, userService.getCurrentUser().getUsername());
  }

  /**
   * Request process instances, that have scheduled operations or locked but with expired locks.
   *
   * @param batchSize
   * @return
   */
  @Override
  public List<OperationEntity> acquireOperations(int batchSize) {
    Query query =
        constantScore(
            or(
                term(OperationTemplate.STATE, SCHEDULED_OPERATION),
                and(
                    term(OperationTemplate.STATE, LOCKED_OPERATION),
                    lte(
                        OperationTemplate.LOCK_EXPIRATION_TIME,
                        dateTimeFormatter.format(OffsetDateTime.now())))));

    var searchRequestBuilder =
        searchRequestBuilder(operationTemplate, ONLY_RUNTIME)
            .sort(sortOptions(BATCH_OPERATION_ID, Asc))
            .from(0)
            .size(batchSize)
            .query(query);

    return richOpenSearchClient.doc().searchValues(searchRequestBuilder, OperationEntity.class);
  }

  @Override
  public Map<Long, List<OperationEntity>> getOperationsPerProcessInstanceKey(
      List<Long> processInstanceKeys) {
    Map<Long, List<OperationEntity>> result = new HashMap<>();

    final Query query =
        constantScore(and(longTerms(PROCESS_INSTANCE_KEY, processInstanceKeys), usernameQuery()));

    var searchRequestBuilder =
        searchRequestBuilder(operationTemplate, ALL)
            .query(query)
            .sort(sortOptions(PROCESS_INSTANCE_KEY, Asc), sortOptions(ID, Asc));

    richOpenSearchClient
        .doc()
        .scrollValues(searchRequestBuilder, OperationEntity.class, true)
        .forEach(
            operationEntity ->
                CollectionUtil.addToMap(
                    result, operationEntity.getProcessInstanceKey(), operationEntity));

    return result;
  }

  @Override
  public Map<Long, List<OperationEntity>> getOperationsPerIncidentKey(String processInstanceId) {
    final Map<Long, List<OperationEntity>> result = new HashMap<>();
    final Query query =
        constantScore(and(term(PROCESS_INSTANCE_KEY, processInstanceId), usernameQuery()));

    var searchRequestBuilder =
        searchRequestBuilder(operationTemplate, ONLY_RUNTIME)
            .query(query)
            .sort(sortOptions(INCIDENT_KEY, Asc), sortOptions(ID, Asc));

    richOpenSearchClient
        .doc()
        .scrollValues(searchRequestBuilder, OperationEntity.class)
        .forEach(
            operationEntity ->
                CollectionUtil.addToMap(result, operationEntity.getIncidentKey(), operationEntity));

    return result;
  }

  @Override
  public Map<String, List<OperationEntity>> getUpdateOperationsPerVariableName(
      Long processInstanceKey, Long scopeKey) {
    final Map<String, List<OperationEntity>> result = new HashMap<>();
    final Query query =
        constantScore(
            and(
                term(PROCESS_INSTANCE_KEY, processInstanceKey),
                term(SCOPE_KEY, scopeKey),
                term(TYPE, OperationType.UPDATE_VARIABLE.name()),
                usernameQuery()));

    var searchRequestBuilder =
        searchRequestBuilder(operationTemplate, ALL).query(query).sort(sortOptions(ID, Asc));

    richOpenSearchClient
        .doc()
        .scrollValues(searchRequestBuilder, OperationEntity.class)
        .forEach(
            operationEntity ->
                CollectionUtil.addToMap(
                    result, operationEntity.getVariableName(), operationEntity));

    return result;
  }

  @Override
  public List<OperationEntity> getOperationsByProcessInstanceKey(Long processInstanceKey) {
    final Query query =
        constantScore(
            and(
                processInstanceKey == null ? null : term(PROCESS_INSTANCE_KEY, processInstanceKey),
                usernameQuery()));

    var searchRequestBuilder =
        searchRequestBuilder(operationTemplate, ALL).query(query).sort(sortOptions(ID, Asc));

    return richOpenSearchClient.doc().scrollValues(searchRequestBuilder, OperationEntity.class);
  }

  // this query will be extended
  @Override
  public List<BatchOperationEntity> getBatchOperations(int pageSize) {
    final Query query =
        constantScore(
            term(BatchOperationTemplate.USERNAME, userService.getCurrentUser().getUsername()));

    var searchRequestBuilder =
        searchRequestBuilder(batchOperationTemplate, ALL).query(query).size(pageSize);

    return richOpenSearchClient
        .doc()
        .searchValues(searchRequestBuilder, BatchOperationEntity.class);
  }

  @Override
  public List<OperationDto> getOperationsByBatchOperationId(String batchOperationId) {
    var searchRequestBuilder =
        searchRequestBuilder(operationTemplate, ALL)
            .query(term(BATCH_OPERATION_ID, batchOperationId));

    final List<OperationEntity> operationEntities =
        richOpenSearchClient.doc().scrollValues(searchRequestBuilder, OperationEntity.class);
    return DtoCreator.create(operationEntities, OperationDto.class);
  }

  @Override
  public List<OperationDto> getOperations(
      OperationType operationType, String processInstanceId, String scopeId, String variableName) {
    var searchRequestBuilder =
        searchRequestBuilder(operationTemplate, ALL)
            .query(
                and(
                    term(TYPE, operationType.name()),
                    term(PROCESS_INSTANCE_KEY, processInstanceId),
                    term(SCOPE_KEY, scopeId),
                    term(VARIABLE_NAME, variableName)));

    final List<OperationEntity> operationEntities =
        richOpenSearchClient.doc().scrollValues(searchRequestBuilder, OperationEntity.class);
    return DtoCreator.create(operationEntities, OperationDto.class);
  }
}
