/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithOr;
import static io.camunda.operate.util.ElasticsearchUtil.scrollAllStream;
import static io.camunda.operate.util.ElasticsearchUtil.sortOrder;
import static io.camunda.operate.util.ElasticsearchUtil.whereToSearch;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.BATCH_OPERATION_ID;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.BATCH_OPERATION_ID_AGGREGATION;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.ID;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.INCIDENT_KEY;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.ITEM_KEY;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.SCOPE_KEY;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.TYPE;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.VARIABLE_NAME;
import static io.camunda.webapps.schema.entities.operation.OperationState.LOCKED;
import static io.camunda.webapps.schema.entities.operation.OperationState.SCHEDULED;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.ScrollException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class OperationReader extends AbstractReader
    implements io.camunda.operate.webapp.reader.OperationReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(OperationReader.class);

  private static final String SCHEDULED_OPERATION = SCHEDULED.toString();
  private static final String LOCKED_OPERATION = LOCKED.toString();

  @Autowired private OperationTemplate operationTemplate;

  @Autowired private DateTimeFormatter dateTimeFormatter;

  @Autowired private PermissionsService permissionsService;

  /**
   * Request process instances, that have scheduled operations or locked but with expired locks.
   *
   * @param batchSize
   * @return
   */
  @Override
  public List<OperationEntity> acquireOperations(final int batchSize) {
    // filter for operations that are legacy (i.e. do not have the property ITEM_KEY)
    final var legacyOperationsQuery =
        Query.of(q -> q.bool(b -> b.mustNot(ElasticsearchUtil.existsQuery(ITEM_KEY))));
    final var scheduledOperationsQuery =
        ElasticsearchUtil.termsQuery(OperationTemplate.STATE, SCHEDULED_OPERATION);
    final var lockedOperationsQuery =
        ElasticsearchUtil.termsQuery(OperationTemplate.STATE, LOCKED_OPERATION);

    final var lockExpirationTimeBuilder =
        new co.elastic.clients.elasticsearch._types.query_dsl.DateRangeQuery.Builder();
    lockExpirationTimeBuilder.field(OperationTemplate.LOCK_EXPIRATION_TIME);
    lockExpirationTimeBuilder.lte(dateTimeFormatter.format(OffsetDateTime.now()));
    final var lockExpirationTimeQuery =
        lockExpirationTimeBuilder.build()._toRangeQuery()._toQuery();

    final var operationsQuery =
        joinWithAnd(
            legacyOperationsQuery,
            joinWithOr(
                scheduledOperationsQuery,
                joinWithAnd(lockedOperationsQuery, lockExpirationTimeQuery)));

    final var constantScoreQuery = ElasticsearchUtil.constantScoreQuery(operationsQuery);

    final var searchRequest =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(whereToSearch(operationTemplate, ONLY_RUNTIME))
            .query(constantScoreQuery)
            .sort(
                ElasticsearchUtil.sortOrder(
                    BATCH_OPERATION_ID, co.elastic.clients.elasticsearch._types.SortOrder.Asc))
            .from(0)
            .size(batchSize)
            .build();

    try {
      final var searchResponse = es8client.search(searchRequest, OperationEntity.class);
      return searchResponse.hits().hits().stream().map(Hit::source).toList();
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while acquiring operations for execution: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Map<Long, List<OperationEntity>> getOperationsPerProcessInstanceKey(
      final List<Long> processInstanceKeys) {
    final Map<Long, List<OperationEntity>> result = new HashMap<>();
    if (hasNoBatchOperationWildcardPermissions()) {
      return result;
    }

    final var es8Query =
        ElasticsearchUtil.constantScoreQuery(
            ElasticsearchUtil.termsQuery(PROCESS_INSTANCE_KEY, processInstanceKeys));

    final var searchRequestBuilder =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(whereToSearch(operationTemplate, ALL))
            .query(es8Query)
            .sort(
                sortOrder(
                    PROCESS_INSTANCE_KEY, co.elastic.clients.elasticsearch._types.SortOrder.Asc),
                sortOrder(ID, co.elastic.clients.elasticsearch._types.SortOrder.Asc));

    try {
      final var resStream = scrollAllStream(es8client, searchRequestBuilder, OperationEntity.class);
      return resStream
          .flatMap(res -> res.hits().hits().stream())
          .map(Hit::source)
          .collect(Collectors.groupingBy(OperationEntity::getProcessInstanceKey));

    } catch (final ScrollException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining operations per process instance id: %s",
              e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Map<Long, List<OperationEntity>> getOperationsPerIncidentKey(
      final String processInstanceId) {
    final Map<Long, List<OperationEntity>> result = new HashMap<>();
    if (hasNoBatchOperationWildcardPermissions()) {
      return result;
    }

    final var es8Query =
        ElasticsearchUtil.constantScoreQuery(
            ElasticsearchUtil.termsQuery(PROCESS_INSTANCE_KEY, processInstanceId));

    final var searchRequestBuilder =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(whereToSearch(operationTemplate, ONLY_RUNTIME))
            .query(es8Query)
            .sort(
                sortOrder(INCIDENT_KEY, co.elastic.clients.elasticsearch._types.SortOrder.Asc),
                sortOrder(ID, co.elastic.clients.elasticsearch._types.SortOrder.Asc));

    try {
      final var resStream = scrollAllStream(es8client, searchRequestBuilder, OperationEntity.class);
      return resStream
          .flatMap(res -> res.hits().hits().stream())
          .map(Hit::source)
          .collect(Collectors.groupingBy(OperationEntity::getIncidentKey));

    } catch (final ScrollException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining operations per incident id: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Map<String, List<OperationEntity>> getUpdateOperationsPerVariableName(
      final Long processInstanceKey, final Long scopeKey) {
    final Map<String, List<OperationEntity>> result = new HashMap<>();
    if (hasNoBatchOperationWildcardPermissions()) {
      return result;
    }

    final var query =
        ElasticsearchUtil.constantScoreQuery(
            ElasticsearchUtil.joinWithAnd(
                ElasticsearchUtil.termsQuery(PROCESS_INSTANCE_KEY, processInstanceKey),
                ElasticsearchUtil.termsQuery(SCOPE_KEY, scopeKey),
                ElasticsearchUtil.termsQuery(TYPE, OperationType.UPDATE_VARIABLE.name())));

    final var searchRequestBuilder =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(whereToSearch(operationTemplate, ALL))
            .query(query)
            .sort(
                ElasticsearchUtil.sortOrder(
                    ID, co.elastic.clients.elasticsearch._types.SortOrder.Asc));

    try {
      final var resStream = scrollAllStream(es8client, searchRequestBuilder, OperationEntity.class);
      return resStream
          .flatMap(res -> res.hits().hits().stream())
          .map(Hit::source)
          .collect(Collectors.groupingBy(OperationEntity::getVariableName));

    } catch (final ScrollException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining operations per variable name: %s",
              e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<OperationEntity> getOperationsByProcessInstanceKey(final Long processInstanceKey) {
    if (hasNoBatchOperationWildcardPermissions()) {
      return List.of();
    }
    final var processInstanceQ =
        processInstanceKey == null
            ? Query.of(q -> q.matchAll(m -> m))
            : ElasticsearchUtil.termsQuery(PROCESS_INSTANCE_KEY, processInstanceKey);
    final var query = ElasticsearchUtil.constantScoreQuery(processInstanceQ);

    final var searchRequestBuilder =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(whereToSearch(operationTemplate, ALL))
            .query(query)
            .sort(
                ElasticsearchUtil.sortOrder(
                    ID, co.elastic.clients.elasticsearch._types.SortOrder.Asc));

    try {
      return scrollAllStream(es8client, searchRequestBuilder, OperationEntity.class)
          .flatMap(res -> res.hits().hits().stream())
          .map(Hit::source)
          .toList();
    } catch (final ScrollException e) {
      final String message =
          String.format("Exception occurred, while obtaining operations: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<OperationDto> getOperationsByBatchOperationId(final String batchOperationId) {
    final var query =
        ElasticsearchUtil.joinWithAnd(
            ElasticsearchUtil.termsQuery(BATCH_OPERATION_ID, batchOperationId),
            allowedOperationsQuery());

    final var searchRequestBuilder =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(whereToSearch(operationTemplate, ALL))
            .query(query);

    try {
      final var operationEntities =
          scrollAllStream(es8client, searchRequestBuilder, OperationEntity.class)
              .flatMap(res -> res.hits().hits().stream())
              .map(Hit::source)
              .toList();
      return DtoCreator.create(operationEntities, OperationDto.class);
    } catch (final ScrollException e) {
      final String message =
          String.format(
              "Exception occurred, while searching for operation with batchOperationId: %s",
              e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<OperationDto> getOperations(
      final OperationType operationType,
      final String processInstanceId,
      final String scopeId,
      final String variableName) {
    final var query =
        ElasticsearchUtil.joinWithAnd(
            ElasticsearchUtil.termsQuery(TYPE, operationType),
            ElasticsearchUtil.termsQuery(PROCESS_INSTANCE_KEY, processInstanceId),
            ElasticsearchUtil.termsQuery(SCOPE_KEY, scopeId),
            ElasticsearchUtil.termsQuery(VARIABLE_NAME, variableName));

    final var searchRequestBuilder =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(whereToSearch(operationTemplate, ALL))
            .query(query);

    try {
      final var operationEntities =
          scrollAllStream(es8client, searchRequestBuilder, OperationEntity.class)
              .flatMap(res -> res.hits().hits().stream())
              .map(Hit::source)
              .toList();
      return DtoCreator.create(operationEntities, OperationDto.class);
    } catch (final ScrollException e) {
      final String message =
          String.format(
              "Exception occurred, while searching for batch operation metadata: %s",
              e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  /* Returns StringTermsAggregate with buckets aggregated by BATCH_OPERATION_ID (and provided sub-aggregations) */
  public co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate
      getOperationsAggregatedByBatchOperationId(
          final List<String> batchOperationIds,
          final Map<String, co.elastic.clients.elasticsearch._types.aggregations.Aggregation>
              subAggregations) {
    final var idsQuery =
        ElasticsearchUtil.termsQuery(OperationTemplate.BATCH_OPERATION_ID, batchOperationIds);

    final var batchIdAggregation =
        co.elastic.clients.elasticsearch._types.aggregations.Aggregation.of(
            a ->
                a.terms(t -> t.field(OperationTemplate.BATCH_OPERATION_ID))
                    .aggregations(subAggregations));

    final var query = ElasticsearchUtil.constantScoreQuery(idsQuery);

    final var searchRequest =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(operationTemplate.getAlias())
            .query(query)
            .size(0)
            .aggregations(BATCH_OPERATION_ID_AGGREGATION, batchIdAggregation)
            .build();

    try {
      final var searchResponse = es8client.search(searchRequest, OperationEntity.class);
      return searchResponse.aggregations().get(BATCH_OPERATION_ID_AGGREGATION).sterms();
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while searching and aggregating operations by batch operation id: %s",
              e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private Query allowedOperationsQuery() {
    final var allowed = permissionsService.getBatchOperationsWithPermission(PermissionType.READ);
    return allowed.isAll()
        ? Query.of(q -> q.matchAll(m -> m))
        : ElasticsearchUtil.termsQuery(BATCH_OPERATION_ID, allowed.getIds());
  }

  private boolean hasNoBatchOperationWildcardPermissions() {
    return !permissionsService.getBatchOperationsWithPermission(PermissionType.READ).isAll();
  }
}
