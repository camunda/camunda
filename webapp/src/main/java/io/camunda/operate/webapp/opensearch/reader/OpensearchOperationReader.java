/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.opensearch.reader;

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
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchOperationReader extends OpensearchAbstractReader implements OperationReader {

  private static final Logger logger = LoggerFactory.getLogger(OpensearchOperationReader.class);

  private static final String SCHEDULED_OPERATION = SCHEDULED.toString();
  private static final String LOCKED_OPERATION = LOCKED.toString();

  @Autowired
  private OperationTemplate operationTemplate;

  @Autowired
  private BatchOperationTemplate batchOperationTemplate;

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  @Autowired
  private UserService userService;

  @Autowired
  RichOpenSearchClient richOpenSearchClient;

  private Query usernameQuery() {
    return term(OperationTemplate.USERNAME, userService.getCurrentUser().getUsername());
  }

  /**
   * Request process instances, that have scheduled operations or locked but with expired locks.
   * @param batchSize
   * @return
   */
  @Override
  public List<OperationEntity> acquireOperations(int batchSize) {
    Query query = constantScore(
      or(
        term(OperationTemplate.STATE, SCHEDULED_OPERATION),
        and(
          term(OperationTemplate.STATE, LOCKED_OPERATION),
          lte(OperationTemplate.LOCK_EXPIRATION_TIME, dateTimeFormatter.format(OffsetDateTime.now()))
        )
      )
    );

    var searchRequestBuilder = searchRequestBuilder(operationTemplate, ONLY_RUNTIME)
      .sort(sortOptions(BATCH_OPERATION_ID, Asc))
      .from(0)
      .size(batchSize)
      .query(query);

    return richOpenSearchClient.doc().searchValues(searchRequestBuilder, OperationEntity.class);
  }

  @Override
  public Map<Long, List<OperationEntity>> getOperationsPerProcessInstanceKey(List<Long> processInstanceKeys) {
    Map<Long, List<OperationEntity>> result = new HashMap<>();

    final Query query = constantScore(
      and(
        longTerms(PROCESS_INSTANCE_KEY, processInstanceKeys),
        usernameQuery()
      )
    );

    var searchRequestBuilder = searchRequestBuilder(operationTemplate, ALL)
      .query(query)
      .sort(
        sortOptions(PROCESS_INSTANCE_KEY, Asc),
        sortOptions(ID, Asc)
      );

    richOpenSearchClient.doc().scrollValues(searchRequestBuilder, OperationEntity.class, true)
      .forEach( operationEntity -> CollectionUtil.addToMap(result, operationEntity.getProcessInstanceKey(), operationEntity));

    return result;
  }

  @Override
  public Map<Long, List<OperationEntity>> getOperationsPerIncidentKey(String processInstanceId) {
    final Map<Long, List<OperationEntity>> result = new HashMap<>();
    final Query query = constantScore(
      and(
        term(PROCESS_INSTANCE_KEY, processInstanceId),
        usernameQuery()
      )
    );

    var searchRequestBuilder = searchRequestBuilder(operationTemplate, ONLY_RUNTIME)
      .query(query)
      .sort(
        sortOptions(INCIDENT_KEY, Asc),
        sortOptions(ID, Asc)
      );

    richOpenSearchClient.doc().scrollValues(searchRequestBuilder, OperationEntity.class)
      .forEach(operationEntity -> CollectionUtil.addToMap(result, operationEntity.getIncidentKey(), operationEntity));

    return result;
  }

  @Override
  public Map<String, List<OperationEntity>> getUpdateOperationsPerVariableName(Long processInstanceKey, Long scopeKey) {
    final Map<String, List<OperationEntity>> result = new HashMap<>();
    final Query query = constantScore(
      and(
        term(PROCESS_INSTANCE_KEY, processInstanceKey),
        term(SCOPE_KEY, scopeKey),
        term(TYPE, OperationType.UPDATE_VARIABLE.name()),
        usernameQuery()
      )
    );

    var searchRequestBuilder = searchRequestBuilder(operationTemplate, ALL)
      .query(query)
      .sort(sortOptions(ID, Asc));

    richOpenSearchClient.doc().scrollValues(searchRequestBuilder, OperationEntity.class)
      .forEach(operationEntity -> CollectionUtil.addToMap(result, operationEntity.getVariableName(), operationEntity));

    return result;
  }

  @Override
  public List<OperationEntity> getOperationsByProcessInstanceKey(Long processInstanceKey) {
    final Query query = constantScore(
      and(
        processInstanceKey == null ? null : term(PROCESS_INSTANCE_KEY, processInstanceKey),
        usernameQuery()
      )
    );

    var searchRequestBuilder = searchRequestBuilder(operationTemplate, ALL)
      .query(query)
      .sort(sortOptions(ID, Asc));

    return richOpenSearchClient.doc().scrollValues(searchRequestBuilder, OperationEntity.class);
  }

  //this query will be extended
  @Override
  public List<BatchOperationEntity> getBatchOperations(int pageSize){
    final Query query = constantScore(
      term(BatchOperationTemplate.USERNAME, userService.getCurrentUser().getUsername())
    );

    var searchRequestBuilder = searchRequestBuilder(batchOperationTemplate, ALL)
      .query(query)
      .size(pageSize);

    return richOpenSearchClient.doc().searchValues(searchRequestBuilder, BatchOperationEntity.class);
  }

  @Override
  public List<OperationDto> getOperationsByBatchOperationId(String batchOperationId) {
    var searchRequestBuilder = searchRequestBuilder(operationTemplate, ALL)
      .query(term(BATCH_OPERATION_ID, batchOperationId));

    final List<OperationEntity> operationEntities = richOpenSearchClient.doc().scrollValues(searchRequestBuilder, OperationEntity.class);
    return DtoCreator.create(operationEntities, OperationDto.class);
  }

  @Override
  public List<OperationDto> getOperations(OperationType operationType, String processInstanceId, String scopeId, String variableName) {
    var searchRequestBuilder = searchRequestBuilder(operationTemplate, ALL)
      .query(and(
        term(TYPE, operationType.name()),
        term(PROCESS_INSTANCE_KEY, processInstanceId),
        term(SCOPE_KEY, scopeId),
        term(VARIABLE_NAME, variableName)
      ));

    final List<OperationEntity> operationEntities = richOpenSearchClient.doc().scrollValues(searchRequestBuilder, OperationEntity.class);
    return DtoCreator.create(operationEntities, OperationDto.class);
  }
}
