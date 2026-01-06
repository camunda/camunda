/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.MAP_CLASS;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.scrollAllStream;
import static io.camunda.operate.util.ElasticsearchUtil.searchAfterToFieldValues;
import static io.camunda.operate.util.ElasticsearchUtil.whereToSearch;
import static io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListRequestDto.SORT_BY_PROCESS_INSTANCE_ID;
import static io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_DEFINITION_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_NAME;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.ELEMENT_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.EVALUATED_INPUTS;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.EVALUATED_OUTPUTS;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.EVALUATION_DATE;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.EXECUTION_INDEX;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.RESULT;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.ROOT_DECISION_DEFINITION_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.ROOT_DECISION_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.ROOT_DECISION_NAME;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.STATE;
import static io.camunda.webapps.schema.entities.dmn.DecisionInstanceState.EVALUATED;
import static io.camunda.webapps.schema.entities.dmn.DecisionInstanceState.FAILED;
import static java.util.stream.Collectors.groupingBy;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.DateRangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.google.common.collect.ImmutableList;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.ScrollException;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.Tuple;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.operate.webapp.rest.dto.dmn.DRDDataEntryDto;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceForListDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListQueryDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListRequestDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListResponseDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceState;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class DecisionInstanceReader extends AbstractReader
    implements io.camunda.operate.webapp.reader.DecisionInstanceReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(DecisionInstanceReader.class);

  @Autowired private PermissionsService permissionsService;
  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;
  @Autowired private DateTimeFormatter dateTimeFormatter;
  @Autowired private OperateProperties operateProperties;

  @Override
  public DecisionInstanceDto getDecisionInstance(final String decisionInstanceId) {
    final var query =
        ElasticsearchUtil.joinWithAnd(
            ElasticsearchUtil.idsQuery(String.valueOf(decisionInstanceId)),
            ElasticsearchUtil.termsQuery(ID, decisionInstanceId));

    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var constantScoreQuery = ElasticsearchUtil.constantScoreQuery(tenantAwareQuery);

    final var request =
        new SearchRequest.Builder()
            .index(whereToSearch(decisionInstanceTemplate, ALL))
            .query(constantScoreQuery)
            .build();

    try {
      final var response = es8client.search(request, DecisionInstanceEntity.class);
      if (response.hits().total().value() == 1) {
        final var decisionInstance = response.hits().hits().get(0).source();
        return DtoCreator.create(decisionInstance, DecisionInstanceDto.class);
      } else if (response.hits().total().value() > 1) {
        throw new NotFoundException(
            String.format(
                "Could not find unique decision instance with id '%s'.", decisionInstanceId));
      } else {
        throw new NotFoundException(
            String.format("Could not find decision instance with id '%s'.", decisionInstanceId));
      }
    } catch (final IOException ex) {
      throw new OperateRuntimeException(ex.getMessage(), ex);
    }
  }

  @Override
  public DecisionInstanceListResponseDto queryDecisionInstances(
      final DecisionInstanceListRequestDto request) {
    final DecisionInstanceListResponseDto result = new DecisionInstanceListResponseDto();

    final List<DecisionInstanceEntity> entities = queryDecisionInstancesEntities(request, result);

    result.setDecisionInstances(DecisionInstanceForListDto.createFrom(entities, objectMapper));

    return result;
  }

  @Override
  public Map<String, List<DRDDataEntryDto>> getDecisionInstanceDRDData(
      final String decisionInstanceId) {
    // we need to find all decision instances with he same key, which we extract from
    // decisionInstanceId
    final var decisionInstanceKey = DecisionInstanceEntity.extractKey(decisionInstanceId);
    final var query = ElasticsearchUtil.termsQuery(KEY, decisionInstanceKey);
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);
    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(whereToSearch(decisionInstanceTemplate, ALL))
            .query(tenantAwareQuery)
            .source(s -> s.filter(f -> f.includes(DECISION_ID, STATE)))
            .sort(ElasticsearchUtil.sortOrder(EVALUATION_DATE, SortOrder.Asc));

    try {
      final var entries =
          scrollAllStream(es8client, searchRequestBuilder, MAP_CLASS)
              .flatMap(res -> res.hits().hits().stream())
              .map(
                  hit ->
                      new DRDDataEntryDto(
                          hit.id(),
                          hit.source().get(DECISION_ID).toString(),
                          DecisionInstanceState.valueOf(hit.source().get(STATE).toString())))
              .toList();

      return entries.stream().collect(groupingBy(DRDDataEntryDto::getDecisionId));
    } catch (final ScrollException e) {
      throw new OperateRuntimeException(
          "Exception occurred while quiering DRD data for decision instance id: "
              + decisionInstanceId);
    }
  }

  @Override
  public Tuple<String, String> getCalledDecisionInstanceAndDefinitionByFlowNodeInstanceId(
      final String flowNodeInstanceId) {
    final var query = ElasticsearchUtil.termsQuery(ELEMENT_INSTANCE_KEY, flowNodeInstanceId);
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);
    final var searchRequest =
        new SearchRequest.Builder()
            .index(whereToSearch(decisionInstanceTemplate, ALL))
            .query(tenantAwareQuery)
            .source(
                s ->
                    s.filter(
                        f ->
                            f.includes(
                                ROOT_DECISION_DEFINITION_ID,
                                ROOT_DECISION_NAME,
                                ROOT_DECISION_ID,
                                DECISION_DEFINITION_ID,
                                DECISION_NAME,
                                DecisionIndex.DECISION_ID)))
            .sort(ElasticsearchUtil.sortOrder(EVALUATION_DATE, SortOrder.Desc))
            .sort(ElasticsearchUtil.sortOrder(EXECUTION_INDEX, SortOrder.Desc))
            .size(1)
            .build();

    try {
      final var response = es8client.search(searchRequest, MAP_CLASS);
      if (response.hits().total().value() >= 1) {
        final var hit = response.hits().hits().get(0);
        return buildCalledDecisionTuple(hit.id(), hit.source());
      }
      return Tuple.of(null, null);
    } catch (final IOException e) {
      final var message =
          String.format(
              "Exception occurred, while obtaining calls decision instance id for flow node instance: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private Tuple<String, String> buildCalledDecisionTuple(
      final String hitId, final Map<String, Object> source) {
    final var rootDecisionDefId = (String) source.get(ROOT_DECISION_DEFINITION_ID);
    final var decisionDefId = (String) source.get(DECISION_DEFINITION_ID);
    final var isRootDecision = rootDecisionDefId.equals(decisionDefId);

    final var instanceId = isRootDecision ? hitId : null;
    final var nameField = isRootDecision ? DECISION_NAME : ROOT_DECISION_NAME;
    final var fallbackField = isRootDecision ? DecisionIndex.DECISION_ID : ROOT_DECISION_ID;
    final var definitionName = (String) source.getOrDefault(nameField, source.get(fallbackField));

    return Tuple.of(instanceId, definitionName);
  }

  private List<DecisionInstanceEntity> queryDecisionInstancesEntities(
      final DecisionInstanceListRequestDto request, final DecisionInstanceListResponseDto result) {
    final var query = createRequestQuery(request.getQuery());

    LOGGER.debug("Decision instance search request: \n{}", query.toString());

    final var searchRequest = createSearchRequest(request, query);

    LOGGER.debug("Search request will search in: \n{}", searchRequest.index());

    try {
      final var response = es8client.search(searchRequest, DecisionInstanceEntity.class);
      result.setTotalCount(response.hits().total().value());

      final var decisionInstanceEntities =
          response.hits().hits().stream()
              .map(
                  hit -> {
                    final var entity = hit.source();
                    entity.setSortValues(hit.sort().stream().map(FieldValue::_get).toArray());
                    return entity;
                  })
              .toList();
      if (request.getSearchBefore() != null) {
        return ImmutableList.copyOf(decisionInstanceEntities).reverse();
      }
      return decisionInstanceEntities;
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining instances list: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private SearchRequest createSearchRequest(
      final DecisionInstanceListRequestDto request, final Query query) {

    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(whereToSearch(decisionInstanceTemplate, ALL))
            .query(tenantAwareQuery)
            .source(
                s ->
                    s.filter(
                        f -> f.excludes(List.of(RESULT, EVALUATED_INPUTS, EVALUATED_OUTPUTS))));

    applySorting(searchRequestBuilder, request);

    return searchRequestBuilder.build();
  }

  private void applySorting(
      final SearchRequest.Builder searchRequestBuilder,
      final DecisionInstanceListRequestDto request) {

    final var sortBy = getSortBy(request);

    final var directSorting = request.getSearchAfter() != null || request.getSearchBefore() == null;
    if (request.getSorting() != null) {
      final var sort1DirectOrder =
          ElasticsearchUtil.toSortOrder(request.getSorting().getSortOrder());
      final var sort1 =
          directSorting
              ? ElasticsearchUtil.sortOrder(sortBy, sort1DirectOrder, "_last")
              : ElasticsearchUtil.sortOrder(
                  sortBy, ElasticsearchUtil.reverseOrder(sort1DirectOrder), "_first");
      searchRequestBuilder.sort(sort1);
    }

    final var sort2And3DirectOrder = directSorting ? SortOrder.Asc : SortOrder.Desc;
    final var sort2 = ElasticsearchUtil.sortOrder(KEY, sort2And3DirectOrder);
    final var sort3 = ElasticsearchUtil.sortOrder(EXECUTION_INDEX, sort2And3DirectOrder);
    final var querySearchAfter =
        directSorting
            ? request.getSearchAfter(objectMapper)
            : request.getSearchBefore(objectMapper);

    searchRequestBuilder.sort(sort2).sort(sort3).size(request.getPageSize());
    if (querySearchAfter != null) {
      searchRequestBuilder.searchAfter(searchAfterToFieldValues(querySearchAfter));
    }
  }

  private String getSortBy(final DecisionInstanceListRequestDto request) {
    if (request.getSorting() != null) {
      String sortBy = request.getSorting().getSortBy();
      if (sortBy.equals(DecisionInstanceListRequestDto.SORT_BY_ID)) {
        // we sort by id as numbers, not as strings
        sortBy = KEY;
      } else if (sortBy.equals(DecisionInstanceListRequestDto.SORT_BY_TENANT_ID)) {
        sortBy = TENANT_ID;
      } else if (sortBy.equals(SORT_BY_PROCESS_INSTANCE_ID)) {
        sortBy = PROCESS_INSTANCE_KEY;
      }
      return sortBy;
    }
    return null;
  }

  private Query createRequestQuery(final DecisionInstanceListQueryDto query) {
    var queryBuilder =
        ElasticsearchUtil.joinWithAnd(
            createEvaluatedFailedQuery(query),
            createDecisionDefinitionIdsQuery(query),
            createIdsQuery(query),
            createProcessInstanceIdQuery(query),
            createEvaluationDateQuery(query),
            createReadPermissionQuery(),
            createTenantIdQuery(query));
    if (queryBuilder == null) {
      queryBuilder = ElasticsearchUtil.matchAllQuery();
    }
    return queryBuilder;
  }

  private Query createTenantIdQuery(final DecisionInstanceListQueryDto query) {
    if (query.getTenantId() != null) {
      return ElasticsearchUtil.termsQuery(DecisionInstanceTemplate.TENANT_ID, query.getTenantId());
    }
    return null;
  }

  private Query createReadPermissionQuery() {
    final var allowed =
        permissionsService.getDecisionsWithPermission(PermissionType.READ_DECISION_INSTANCE);
    return allowed.isAll()
        ? ElasticsearchUtil.matchAllQuery()
        : ElasticsearchUtil.termsQuery(DecisionIndex.DECISION_ID, allowed.getIds());
  }

  private Query createEvaluationDateQuery(final DecisionInstanceListQueryDto query) {
    if (query.getEvaluationDateAfter() != null || query.getEvaluationDateBefore() != null) {
      final var dateRangeBuilder =
          new DateRangeQuery.Builder()
              .field(EVALUATION_DATE)
              .format(operateProperties.getElasticsearch().getElsDateFormat());

      if (query.getEvaluationDateAfter() != null) {
        dateRangeBuilder.gte(dateTimeFormatter.format(query.getEvaluationDateAfter()));
      }
      if (query.getEvaluationDateBefore() != null) {
        dateRangeBuilder.lt(dateTimeFormatter.format(query.getEvaluationDateBefore()));
      }

      return new RangeQuery.Builder().date(dateRangeBuilder.build()).build()._toQuery();
    }
    return null;
  }

  private Query createProcessInstanceIdQuery(final DecisionInstanceListQueryDto query) {
    if (query.getProcessInstanceId() != null) {
      return ElasticsearchUtil.termsQuery(PROCESS_INSTANCE_KEY, query.getProcessInstanceId());
    }
    return null;
  }

  private Query createIdsQuery(final DecisionInstanceListQueryDto query) {
    if (CollectionUtil.isNotEmpty(query.getIds())) {
      return ElasticsearchUtil.termsQuery(ID, query.getIds());
    }
    return null;
  }

  private Query createDecisionDefinitionIdsQuery(final DecisionInstanceListQueryDto query) {
    if (CollectionUtil.isNotEmpty(query.getDecisionDefinitionIds())) {
      return ElasticsearchUtil.termsQuery(DECISION_DEFINITION_ID, query.getDecisionDefinitionIds());
    }
    return null;
  }

  private Query createEvaluatedFailedQuery(final DecisionInstanceListQueryDto query) {
    if (query.isEvaluated() && query.isFailed()) {
      // cover all instances
      return null;
    } else if (query.isFailed()) {
      return ElasticsearchUtil.termsQuery(STATE, FAILED);
    } else if (query.isEvaluated()) {
      return ElasticsearchUtil.termsQuery(STATE, EVALUATED);
    } else {
      return ElasticsearchUtil.createMatchNoneQueryEs8().build()._toQuery();
    }
  }
}
