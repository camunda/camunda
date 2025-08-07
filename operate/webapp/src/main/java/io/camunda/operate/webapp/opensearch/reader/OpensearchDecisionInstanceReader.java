/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.CollectionUtil.toSafeListOfStrings;
import static io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListRequestDto.SORT_BY_PROCESS_INSTANCE_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.*;
import static io.camunda.webapps.schema.entities.dmn.DecisionInstanceState.EVALUATED;
import static io.camunda.webapps.schema.entities.dmn.DecisionInstanceState.FAILED;
import static java.util.stream.Collectors.groupingBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.Tuple;
import io.camunda.operate.webapp.reader.DecisionInstanceReader;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.operate.webapp.rest.dto.dmn.DRDDataEntryDto;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceForListDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListQueryDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListRequestDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListResponseDto;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceState;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchDecisionInstanceReader implements DecisionInstanceReader {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpensearchDecisionInstanceReader.class);

  private final DecisionInstanceTemplate decisionInstanceTemplate;

  private final DateTimeFormatter dateTimeFormatter;

  private final ObjectMapper objectMapper;

  private final OperateProperties operateProperties;

  private final PermissionsService permissionsService;

  private final RichOpenSearchClient richOpenSearchClient;

  public OpensearchDecisionInstanceReader(
      final DecisionInstanceTemplate decisionInstanceTemplate,
      final DateTimeFormatter dateTimeFormatter,
      final ObjectMapper objectMapper,
      final OperateProperties operateProperties,
      final PermissionsService permissionsService,
      final RichOpenSearchClient richOpenSearchClient) {
    this.decisionInstanceTemplate = decisionInstanceTemplate;
    this.dateTimeFormatter = dateTimeFormatter;
    this.objectMapper = objectMapper;
    this.operateProperties = operateProperties;
    this.permissionsService = permissionsService;
    this.richOpenSearchClient = richOpenSearchClient;
  }

  @Override
  public DecisionInstanceDto getDecisionInstance(final String decisionInstanceId) {
    final var searchRequest =
        searchRequestBuilder(decisionInstanceTemplate)
            .query(
                withTenantCheck(
                    constantScore(
                        and(
                            ids(decisionInstanceId),
                            term(DecisionInstanceTemplate.ID, decisionInstanceId)))));

    try {
      return DtoCreator.create(
          richOpenSearchClient
              .doc()
              .searchUnique(searchRequest, DecisionInstanceEntity.class, decisionInstanceId),
          DecisionInstanceDto.class);
    } catch (final NotFoundException e) {
      throw new io.camunda.operate.webapp.rest.exception.NotFoundException(e.getMessage());
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
    // we need to find all decision instances with the same key, which we extract from
    // decisionInstanceId
    final Long decisionInstanceKey = DecisionInstanceEntity.extractKey(decisionInstanceId);

    final var searchRequest =
        searchRequestBuilder(decisionInstanceTemplate)
            .query(withTenantCheck(term(DecisionInstanceTemplate.KEY, decisionInstanceKey)))
            .source(sourceInclude(DECISION_ID, STATE));

    final List<DRDDataEntryDto> results = new ArrayList<>();
    richOpenSearchClient
        .doc()
        .scrollWith(
            searchRequest,
            Map.class,
            hits ->
                hits.stream()
                    .filter(hit -> hit.source() != null)
                    .forEach(
                        hit -> {
                          final var map = hit.source();
                          results.add(
                              new DRDDataEntryDto(
                                  hit.id(),
                                  (String) map.get(DECISION_ID),
                                  DecisionInstanceState.valueOf((String) map.get(STATE))));
                        }));
    return results.stream().collect(groupingBy(DRDDataEntryDto::getDecisionId));
  }

  @Override
  public Tuple<String, String> getCalledDecisionInstanceAndDefinitionByFlowNodeInstanceId(
      final String flowNodeInstanceId) {
    final String[] calledDecisionInstanceId = {null};
    final String[] calledDecisionDefinitionName = {null};
    findCalledDecisionInstance(
        flowNodeInstanceId,
        hit -> {
          final var source = hit.source();
          final var rootDecisionDefId = source.getRootDecisionDefinitionId();
          final var decisionDefId = source.getDecisionDefinitionId();
          if (rootDecisionDefId.equals(decisionDefId)) {
            // this is our instance, we will show the link
            calledDecisionInstanceId[0] = source.getId();
            var decisionName = source.getDecisionName();
            if (decisionName == null) {
              decisionName = source.getDecisionId();
            }
            calledDecisionDefinitionName[0] = decisionName;
          } else {
            // we will show only name of the root decision without the link
            var decisionName = source.getRootDecisionName();
            if (decisionName == null) {
              decisionName = source.getRootDecisionId();
            }
            calledDecisionDefinitionName[0] = decisionName;
          }
        });

    return Tuple.of(calledDecisionInstanceId[0], calledDecisionDefinitionName[0]);
  }

  private List<DecisionInstanceEntity> queryDecisionInstancesEntities(
      final DecisionInstanceListRequestDto request, final DecisionInstanceListResponseDto result) {
    final var query = createRequestQuery(request.getQuery());

    LOGGER.debug("Decision instance search request: \n{}", query);

    final var searchRequest =
        searchRequestBuilder(decisionInstanceTemplate)
            .query(query)
            .source(sourceExclude(RESULT, EVALUATED_INPUTS, EVALUATED_OUTPUTS));

    applySorting(searchRequest, request);

    LOGGER.debug(
        "Search request will search in: \n{}", decisionInstanceTemplate.getFullQualifiedName());

    final var response =
        richOpenSearchClient.doc().search(searchRequest, DecisionInstanceEntity.class);
    result.setTotalCount(response.hits().total().value());

    final List<DecisionInstanceEntity> decisionInstanceEntities =
        new ArrayList<>(
            response.hits().hits().stream()
                .filter(hit -> hit.source() != null)
                .map(hit -> hit.source().setSortValues(hit.sort().toArray()))
                .toList());
    if (request.getSearchBefore() != null) {
      Collections.reverse(decisionInstanceEntities);
    }
    return decisionInstanceEntities;
  }

  private Query createRequestQuery(
      final DecisionInstanceListQueryDto decisionInstanceListQueryDto) {
    final var query =
        withTenantCheck(
            and(
                createEvaluatedFailedQuery(decisionInstanceListQueryDto),
                createDecisionDefinitionIdsQuery(decisionInstanceListQueryDto),
                createIdsQuery(decisionInstanceListQueryDto),
                createProcessInstanceIdQuery(decisionInstanceListQueryDto),
                createEvaluationDateQuery(decisionInstanceListQueryDto),
                createReadPermissionQuery(),
                createTenantIdQuery(decisionInstanceListQueryDto)));
    return query == null ? matchAll() : query;
  }

  private Query createTenantIdQuery(final DecisionInstanceListQueryDto query) {
    if (query.getTenantId() != null) {
      return term(DecisionInstanceTemplate.TENANT_ID, query.getTenantId());
    }
    return null;
  }

  private Query createReadPermissionQuery() {
    final var allowed =
        permissionsService.getDecisionsWithPermission(PermissionType.READ_DECISION_INSTANCE);
    return allowed.isAll() ? matchAll() : stringTerms(DecisionIndex.DECISION_ID, allowed.getIds());
  }

  private Query createEvaluationDateQuery(
      final DecisionInstanceListQueryDto decisionInstanceListQueryDto) {
    if (decisionInstanceListQueryDto.getEvaluationDateAfter() != null
        || decisionInstanceListQueryDto.getEvaluationDateBefore() != null) {
      final var query =
          RangeQuery.of(
              q -> {
                q.field(EVALUATION_DATE);
                if (decisionInstanceListQueryDto.getEvaluationDateAfter() != null) {
                  q.gte(
                      JsonData.of(
                          dateTimeFormatter.format(
                              decisionInstanceListQueryDto.getEvaluationDateAfter())));
                }
                if (decisionInstanceListQueryDto.getEvaluationDateBefore() != null) {
                  q.lt(
                      JsonData.of(
                          dateTimeFormatter.format(
                              decisionInstanceListQueryDto.getEvaluationDateBefore())));
                }
                q.format(operateProperties.getOpensearch().getDateFormat());
                return q;
              });
      return query._toQuery();
    }
    return null;
  }

  private Query createProcessInstanceIdQuery(final DecisionInstanceListQueryDto query) {
    if (query.getProcessInstanceId() != null) {
      return term(PROCESS_INSTANCE_KEY, query.getProcessInstanceId());
    }
    return null;
  }

  private Query createIdsQuery(final DecisionInstanceListQueryDto query) {
    if (CollectionUtil.isNotEmpty(query.getIds())) {
      return stringTerms(ID, query.getIds());
    }
    return null;
  }

  private Query createDecisionDefinitionIdsQuery(final DecisionInstanceListQueryDto query) {
    if (CollectionUtil.isNotEmpty(query.getDecisionDefinitionIds())) {
      return stringTerms(DECISION_DEFINITION_ID, query.getDecisionDefinitionIds());
    }
    return null;
  }

  private Query createEvaluatedFailedQuery(final DecisionInstanceListQueryDto query) {
    if (query.isEvaluated() && query.isFailed()) {
      // cover all instances
      return null;
    } else if (query.isFailed()) {
      return term(STATE, FAILED.name());
    } else if (query.isEvaluated()) {
      return term(STATE, EVALUATED.name());
    } else {
      return matchNone();
    }
  }

  private void applySorting(
      final SearchRequest.Builder searchRequest, final DecisionInstanceListRequestDto request) {
    final String sortBy = getSortBy(request);

    final boolean directSorting =
        request.getSearchAfter() != null || request.getSearchBefore() == null;
    if (request.getSorting() != null) {
      final SortOptions sort1;
      final SortOrder sort1DirectOrder =
          request.getSorting().getSortOrder().equalsIgnoreCase("desc")
              ? SortOrder.Desc
              : SortOrder.Asc;
      if (directSorting) {
        sort1 = sortOptions(sortBy, sort1DirectOrder, "_last");
      } else {
        sort1 = sortOptions(sortBy, reverseOrder(sort1DirectOrder), "_first");
      }
      searchRequest.sort(sort1);
    }

    final SortOptions sort2;
    final SortOptions sort3;
    final Object[] querySearchAfter;
    if (directSorting) { // this sorting is also the default one for 1st page
      sort2 = sortOptions(KEY, SortOrder.Asc);
      sort3 = sortOptions(EXECUTION_INDEX, SortOrder.Asc);
      querySearchAfter = request.getSearchAfter(objectMapper); // may be null
    } else { // searchBefore != null
      // reverse sorting
      sort2 = sortOptions(KEY, SortOrder.Desc);
      sort3 = sortOptions(EXECUTION_INDEX, SortOrder.Desc);
      querySearchAfter = request.getSearchBefore(objectMapper);
    }

    searchRequest.sort(sort2).sort(sort3).size(request.getPageSize());
    if (querySearchAfter != null) {
      searchRequest.searchAfter(toSafeListOfStrings(querySearchAfter));
    }
  }

  private String getSortBy(final DecisionInstanceListRequestDto request) {
    if (request.getSorting() != null) {
      String sortBy = request.getSorting().getSortBy();
      sortBy =
          switch (sortBy) {
            case DecisionInstanceTemplate.ID ->
                // we sort by id as numbers, not as strings
                KEY;
            case DecisionInstanceListRequestDto.SORT_BY_TENANT_ID -> TENANT_ID;
            case SORT_BY_PROCESS_INSTANCE_ID -> PROCESS_INSTANCE_KEY;
            default -> sortBy;
          };
      return sortBy;
    }
    return null;
  }

  private void findCalledDecisionInstance(
      final String flowNodeInstanceId,
      final Consumer<Hit<DecisionInstanceEntity>> decisionInstanceConsumer) {

    final var searchRequest =
        searchRequestBuilder(decisionInstanceTemplate.getAlias())
            .query(withTenantCheck(term(ELEMENT_INSTANCE_KEY, flowNodeInstanceId)))
            .source(
                sourceInclude(
                    ROOT_DECISION_DEFINITION_ID,
                    ROOT_DECISION_NAME,
                    ROOT_DECISION_ID,
                    DECISION_DEFINITION_ID,
                    DECISION_NAME,
                    DecisionIndex.DECISION_ID))
            .sort(
                List.of(
                    sortOptions(EVALUATION_DATE, SortOrder.Desc),
                    sortOptions(EXECUTION_INDEX, SortOrder.Desc)));
    final var response =
        richOpenSearchClient.doc().search(searchRequest, DecisionInstanceEntity.class);
    if (response.hits().total().value() >= 1) {
      decisionInstanceConsumer.accept(response.hits().hits().get(0));
    }
  }
}
