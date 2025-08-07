/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.constantScore;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.exists;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.longTerms;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.lte;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.not;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.or;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.sortOptions;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ALL;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.BATCH_OPERATION_ID;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.ID;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.INCIDENT_KEY;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.ITEM_KEY;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.SCOPE_KEY;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.TYPE;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.VARIABLE_NAME;
import static io.camunda.webapps.schema.entities.operation.OperationState.LOCKED;
import static io.camunda.webapps.schema.entities.operation.OperationState.SCHEDULED;
import static org.opensearch.client.opensearch._types.SortOrder.Asc;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.dsl.AggregationDSL;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchOperationReader extends OpensearchAbstractReader implements OperationReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchOperationReader.class);

  private static final String SCHEDULED_OPERATION = SCHEDULED.toString();
  private static final String LOCKED_OPERATION = LOCKED.toString();
  @Autowired private OperationTemplate operationTemplate;
  @Autowired private BatchOperationTemplate batchOperationTemplate;
  @Autowired private DateTimeFormatter dateTimeFormatter;
  @Autowired private CamundaAuthenticationProvider camundaAuthenticationProvider;

  private Query usernameQuery() {
    return term(
        OperationTemplate.USERNAME,
        camundaAuthenticationProvider.getCamundaAuthentication().authenticatedUsername());
  }

  /**
   * Request process instances, that have scheduled operations or locked but with expired locks.
   *
   * @param batchSize
   * @return
   */
  @Override
  public List<OperationEntity> acquireOperations(final int batchSize) {
    final Query query =
        constantScore(
            and(
                // filter for operations that are legacy (i.e. do not have the property ITEM_KEY)
                not(exists(ITEM_KEY)),
                or(
                    term(OperationTemplate.STATE, SCHEDULED_OPERATION),
                    and(
                        term(OperationTemplate.STATE, LOCKED_OPERATION),
                        lte(
                            OperationTemplate.LOCK_EXPIRATION_TIME,
                            dateTimeFormatter.format(OffsetDateTime.now()))))));

    final var searchRequestBuilder =
        searchRequestBuilder(operationTemplate, ONLY_RUNTIME)
            .sort(sortOptions(BATCH_OPERATION_ID, Asc))
            .from(0)
            .size(batchSize)
            .query(query);

    return richOpenSearchClient.doc().searchValues(searchRequestBuilder, OperationEntity.class);
  }

  @Override
  public Map<Long, List<OperationEntity>> getOperationsPerProcessInstanceKey(
      final List<Long> processInstanceKeys) {
    final Map<Long, List<OperationEntity>> result = new HashMap<>();

    final Query query =
        constantScore(and(longTerms(PROCESS_INSTANCE_KEY, processInstanceKeys), usernameQuery()));

    final var searchRequestBuilder =
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
  public Map<Long, List<OperationEntity>> getOperationsPerIncidentKey(
      final String processInstanceId) {
    final Map<Long, List<OperationEntity>> result = new HashMap<>();
    final Query query =
        constantScore(and(term(PROCESS_INSTANCE_KEY, processInstanceId), usernameQuery()));

    final var searchRequestBuilder =
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
      final Long processInstanceKey, final Long scopeKey) {
    final Map<String, List<OperationEntity>> result = new HashMap<>();
    final Query query =
        constantScore(
            and(
                term(PROCESS_INSTANCE_KEY, processInstanceKey),
                term(SCOPE_KEY, scopeKey),
                term(TYPE, OperationType.UPDATE_VARIABLE.name()),
                usernameQuery()));

    final var searchRequestBuilder =
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
  public List<OperationEntity> getOperationsByProcessInstanceKey(final Long processInstanceKey) {
    final Query query =
        constantScore(
            and(
                processInstanceKey == null ? null : term(PROCESS_INSTANCE_KEY, processInstanceKey),
                usernameQuery()));

    final var searchRequestBuilder =
        searchRequestBuilder(operationTemplate, ALL).query(query).sort(sortOptions(ID, Asc));

    return richOpenSearchClient.doc().scrollValues(searchRequestBuilder, OperationEntity.class);
  }

  // this query will be extended
  @Override
  public List<BatchOperationEntity> getBatchOperations(final int pageSize) {
    final Query query =
        constantScore(
            term(
                BatchOperationTemplate.USERNAME,
                camundaAuthenticationProvider.getCamundaAuthentication().authenticatedUsername()));

    final var searchRequestBuilder =
        searchRequestBuilder(batchOperationTemplate, ALL).query(query).size(pageSize);

    return richOpenSearchClient
        .doc()
        .searchValues(searchRequestBuilder, BatchOperationEntity.class);
  }

  @Override
  public List<OperationDto> getOperationsByBatchOperationId(final String batchOperationId) {
    final var searchRequestBuilder =
        searchRequestBuilder(operationTemplate, ALL)
            .query(and(term(BATCH_OPERATION_ID, batchOperationId), usernameQuery()));

    final List<OperationEntity> operationEntities =
        richOpenSearchClient.doc().scrollValues(searchRequestBuilder, OperationEntity.class);
    return DtoCreator.create(operationEntities, OperationDto.class);
  }

  @Override
  public List<OperationDto> getOperations(
      final OperationType operationType,
      final String processInstanceId,
      final String scopeId,
      final String variableName) {
    final var searchRequestBuilder =
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

  public Builder getSearchRequestByIdWithMetadata(final String batchOperationId) {
    final Query failedOperationQuery = term(OperationTemplate.STATE, OperationState.FAILED.name());
    final Query completedOperationQuery =
        term(OperationTemplate.STATE, OperationState.COMPLETED.name());
    final var searchRequestBuilder =
        searchRequestBuilder(operationTemplate, ALL)
            .query(term(BATCH_OPERATION_ID, batchOperationId))
            .aggregations(
                OperationTemplate.METADATA_AGGREGATION,
                AggregationDSL.filtersAggregation(
                        Map.of(
                            BatchOperationTemplate.FAILED_OPERATIONS_COUNT, failedOperationQuery,
                            BatchOperationTemplate.COMPLETED_OPERATIONS_COUNT,
                                completedOperationQuery))
                    ._toAggregation());
    return searchRequestBuilder;
  }
}
