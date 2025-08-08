/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.createMatchNoneQuery;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.reverseOrder;
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
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
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
    final QueryBuilder query =
        joinWithAnd(
            idsQuery().addIds(String.valueOf(decisionInstanceId)),
            termQuery(ID, decisionInstanceId));

    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(decisionInstanceTemplate, ALL)
            .source(new SearchSourceBuilder().query(constantScoreQuery(query)));

    try {
      final SearchResponse response = tenantAwareClient.search(request);
      if (response.getHits().getTotalHits().value == 1) {
        final DecisionInstanceEntity decisionInstance =
            ElasticsearchUtil.fromSearchHit(
                response.getHits().getHits()[0].getSourceAsString(),
                objectMapper,
                DecisionInstanceEntity.class);
        return DtoCreator.create(decisionInstance, DecisionInstanceDto.class);
      } else if (response.getHits().getTotalHits().value > 1) {
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
    final Long decisionInstanceKey = DecisionInstanceEntity.extractKey(decisionInstanceId);
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(decisionInstanceTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(termQuery(KEY, decisionInstanceKey))
                    .fetchSource(new String[] {DECISION_ID, STATE}, null)
                    .sort(EVALUATION_DATE, SortOrder.ASC));
    try {
      final List<DRDDataEntryDto> entries =
          tenantAwareClient.search(
              request,
              () -> {
                return ElasticsearchUtil.scroll(
                    request,
                    DRDDataEntryDto.class,
                    objectMapper,
                    esClient,
                    sh -> {
                      final Map<String, Object> map = sh.getSourceAsMap();
                      return new DRDDataEntryDto(
                          sh.getId(),
                          (String) map.get(DECISION_ID),
                          DecisionInstanceState.valueOf((String) map.get(STATE)));
                    },
                    null,
                    null);
              });
      return entries.stream().collect(groupingBy(DRDDataEntryDto::getDecisionId));
    } catch (final IOException e) {
      throw new OperateRuntimeException(
          "Exception occurred while quiering DRD data for decision instance id: "
              + decisionInstanceId);
    }
  }

  @Override
  public Tuple<String, String> getCalledDecisionInstanceAndDefinitionByFlowNodeInstanceId(
      final String flowNodeInstanceId) {
    final String[] calledDecisionInstanceId = {null};
    final String[] calledDecisionDefinitionName = {null};
    findCalledDecisionInstance(
        flowNodeInstanceId,
        sh -> {
          final Map<String, Object> source = sh.getSourceAsMap();
          final String rootDecisionDefId = (String) source.get(ROOT_DECISION_DEFINITION_ID);
          final String decisionDefId = (String) source.get(DECISION_DEFINITION_ID);

          if (rootDecisionDefId.equals(decisionDefId)) {
            // this is our instance, we will show the link
            calledDecisionInstanceId[0] = sh.getId();
            String decisionName = (String) source.get(DECISION_NAME);
            if (decisionName == null) {
              decisionName = (String) source.get(DecisionIndex.DECISION_ID);
            }
            calledDecisionDefinitionName[0] = decisionName;
          } else {
            // we will show only name of the root decision without the link
            String decisionName = (String) source.get(ROOT_DECISION_NAME);
            if (decisionName == null) {
              decisionName = (String) source.get(ROOT_DECISION_ID);
            }
            calledDecisionDefinitionName[0] = decisionName;
          }
        });
    return Tuple.of(calledDecisionInstanceId[0], calledDecisionDefinitionName[0]);
  }

  private List<DecisionInstanceEntity> queryDecisionInstancesEntities(
      final DecisionInstanceListRequestDto request, final DecisionInstanceListResponseDto result) {
    final QueryBuilder query = createRequestQuery(request.getQuery());

    LOGGER.debug("Decision instance search request: \n{}", query.toString());

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(query)
            .fetchSource(null, new String[] {RESULT, EVALUATED_INPUTS, EVALUATED_OUTPUTS});

    applySorting(searchSourceBuilder, request);

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(decisionInstanceTemplate).source(searchSourceBuilder);

    LOGGER.debug("Search request will search in: \n{}", searchRequest.indices());

    try {
      final SearchResponse response = tenantAwareClient.search(searchRequest);
      result.setTotalCount(response.getHits().getTotalHits().value);

      final List<DecisionInstanceEntity> decisionInstanceEntities =
          ElasticsearchUtil.mapSearchHits(
              response.getHits().getHits(),
              (sh) -> {
                final DecisionInstanceEntity entity =
                    ElasticsearchUtil.fromSearchHit(
                        sh.getSourceAsString(), objectMapper, DecisionInstanceEntity.class);
                entity.setSortValues(sh.getSortValues());
                return entity;
              });
      if (request.getSearchBefore() != null) {
        Collections.reverse(decisionInstanceEntities);
      }
      return decisionInstanceEntities;
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining instances list: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private void applySorting(
      final SearchSourceBuilder searchSourceBuilder, final DecisionInstanceListRequestDto request) {

    final String sortBy = getSortBy(request);

    final boolean directSorting =
        request.getSearchAfter() != null || request.getSearchBefore() == null;
    if (request.getSorting() != null) {
      final SortBuilder sort1;
      final SortOrder sort1DirectOrder = SortOrder.fromString(request.getSorting().getSortOrder());
      if (directSorting) {
        sort1 = SortBuilders.fieldSort(sortBy).order(sort1DirectOrder).missing("_last");
      } else {
        sort1 =
            SortBuilders.fieldSort(sortBy).order(reverseOrder(sort1DirectOrder)).missing("_first");
      }
      searchSourceBuilder.sort(sort1);
    }

    final SortBuilder sort2;
    final SortBuilder sort3;
    final Object[] querySearchAfter;
    if (directSorting) { // this sorting is also the default one for 1st page
      sort2 = SortBuilders.fieldSort(KEY).order(SortOrder.ASC);
      sort3 = SortBuilders.fieldSort(EXECUTION_INDEX).order(SortOrder.ASC);
      querySearchAfter = request.getSearchAfter(objectMapper); // may be null
    } else { // searchBefore != null
      // reverse sorting
      sort2 = SortBuilders.fieldSort(KEY).order(SortOrder.DESC);
      sort3 = SortBuilders.fieldSort(EXECUTION_INDEX).order(SortOrder.DESC);
      querySearchAfter = request.getSearchBefore(objectMapper);
    }

    searchSourceBuilder.sort(sort2).sort(sort3).size(request.getPageSize());
    if (querySearchAfter != null) {
      searchSourceBuilder.searchAfter(querySearchAfter);
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

  private QueryBuilder createRequestQuery(final DecisionInstanceListQueryDto query) {
    QueryBuilder queryBuilder =
        joinWithAnd(
            createEvaluatedFailedQuery(query),
            createDecisionDefinitionIdsQuery(query),
            createIdsQuery(query),
            createProcessInstanceIdQuery(query),
            createEvaluationDateQuery(query),
            createReadPermissionQuery(),
            createTenantIdQuery(query));
    if (queryBuilder == null) {
      queryBuilder = matchAllQuery();
    }
    return queryBuilder;
  }

  private QueryBuilder createTenantIdQuery(final DecisionInstanceListQueryDto query) {
    if (query.getTenantId() != null) {
      return termQuery(DecisionInstanceTemplate.TENANT_ID, query.getTenantId());
    }
    return null;
  }

  private QueryBuilder createReadPermissionQuery() {
    final var allowed =
        permissionsService.getDecisionsWithPermission(PermissionType.READ_DECISION_INSTANCE);
    return allowed.isAll()
        ? QueryBuilders.matchAllQuery()
        : QueryBuilders.termsQuery(DecisionIndex.DECISION_ID, allowed.getIds());
  }

  private QueryBuilder createEvaluationDateQuery(final DecisionInstanceListQueryDto query) {
    if (query.getEvaluationDateAfter() != null || query.getEvaluationDateBefore() != null) {
      final RangeQueryBuilder rangeQueryBuilder = rangeQuery(EVALUATION_DATE);
      if (query.getEvaluationDateAfter() != null) {
        rangeQueryBuilder.gte(dateTimeFormatter.format(query.getEvaluationDateAfter()));
      }
      if (query.getEvaluationDateBefore() != null) {
        rangeQueryBuilder.lt(dateTimeFormatter.format(query.getEvaluationDateBefore()));
      }
      rangeQueryBuilder.format(operateProperties.getElasticsearch().getElsDateFormat());

      return rangeQueryBuilder;
    }
    return null;
  }

  private QueryBuilder createProcessInstanceIdQuery(final DecisionInstanceListQueryDto query) {
    if (query.getProcessInstanceId() != null) {
      return termQuery(PROCESS_INSTANCE_KEY, query.getProcessInstanceId());
    }
    return null;
  }

  private QueryBuilder createIdsQuery(final DecisionInstanceListQueryDto query) {
    if (CollectionUtil.isNotEmpty(query.getIds())) {
      return termsQuery(ID, query.getIds());
    }
    return null;
  }

  private QueryBuilder createDecisionDefinitionIdsQuery(final DecisionInstanceListQueryDto query) {
    if (CollectionUtil.isNotEmpty(query.getDecisionDefinitionIds())) {
      return termsQuery(DECISION_DEFINITION_ID, query.getDecisionDefinitionIds());
    }
    return null;
  }

  private QueryBuilder createEvaluatedFailedQuery(final DecisionInstanceListQueryDto query) {
    if (query.isEvaluated() && query.isFailed()) {
      // cover all instances
      return null;
    } else if (query.isFailed()) {
      return termQuery(STATE, FAILED);
    } else if (query.isEvaluated()) {
      return termQuery(STATE, EVALUATED);
    } else {
      return createMatchNoneQuery();
    }
  }

  private void findCalledDecisionInstance(
      final String flowNodeInstanceId, final Consumer<SearchHit> decisionInstanceConsumer) {
    final TermQueryBuilder flowNodeInstanceQ = termQuery(ELEMENT_INSTANCE_KEY, flowNodeInstanceId);
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(decisionInstanceTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(flowNodeInstanceQ)
                    .fetchSource(
                        new String[] {
                          ROOT_DECISION_DEFINITION_ID,
                          ROOT_DECISION_NAME,
                          ROOT_DECISION_ID,
                          DECISION_DEFINITION_ID,
                          DECISION_NAME,
                          DecisionIndex.DECISION_ID
                        },
                        null)
                    .sort(EVALUATION_DATE, SortOrder.DESC)
                    .sort(EXECUTION_INDEX, SortOrder.DESC));
    try {
      final SearchResponse response = tenantAwareClient.search(request);
      if (response.getHits().getTotalHits().value >= 1) {
        decisionInstanceConsumer.accept(response.getHits().getAt(0));
      }
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining calls decision instance id for flow node instance: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }
}
