/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.es.reader;

import static io.camunda.operate.entities.dmn.DecisionInstanceState.EVALUATED;
import static io.camunda.operate.entities.dmn.DecisionInstanceState.FAILED;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.DECISION_DEFINITION_ID;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.DECISION_ID;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.EVALUATED_INPUTS;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.EVALUATED_OUTPUTS;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.EVALUATION_DATE;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.EXECUTION_INDEX;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.ID;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.KEY;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.RESULT;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.STATE;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.createMatchNoneQuery;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListRequestDto.SORT_BY_PROCESS_INSTANCE_ID;
import static java.util.stream.Collectors.groupingBy;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceState;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.operate.webapp.rest.dto.dmn.DRDDataEntryDto;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceForListDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListQueryDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListRequestDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListResponseDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DecisionInstanceReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(DecisionInstanceReader.class);

  @Autowired
  private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired
  private DecisionIndex decisionIndex;

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired(required = false)
  protected PermissionsService permissionsService;

  public DecisionInstanceDto getDecisionInstance(String decisionInstanceId) {
    final QueryBuilder query = joinWithAnd(
        idsQuery().addIds(String.valueOf(decisionInstanceId)),
        termQuery(ID, decisionInstanceId)
    );

    SearchRequest request = ElasticsearchUtil.createSearchRequest(decisionInstanceTemplate, ALL)
      .source(new SearchSourceBuilder()
      .query(constantScoreQuery(query)));

    try {
      final SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value == 1) {
        final DecisionInstanceEntity decisionInstance = ElasticsearchUtil
            .fromSearchHit(response.getHits().getHits()[0].getSourceAsString(), objectMapper,
                DecisionInstanceEntity.class);
        return DtoCreator.create(decisionInstance, DecisionInstanceDto.class);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(String
            .format("Could not find unique decision instance with id '%s'.", decisionInstanceId));
      } else {
        throw new NotFoundException(
            String.format("Could not find decision instance with id '%s'.", decisionInstanceId));
      }
    } catch (IOException ex) {
      throw new OperateRuntimeException(ex.getMessage(), ex);
    }
  }

  public DecisionInstanceListResponseDto queryDecisionInstances(
      final DecisionInstanceListRequestDto request) {
    DecisionInstanceListResponseDto result = new DecisionInstanceListResponseDto();

    List<DecisionInstanceEntity> entities = queryDecisionInstancesEntities(request, result);

    result.setDecisionInstances(DecisionInstanceForListDto.createFrom(entities, objectMapper));

    return result;
  }

  private List<DecisionInstanceEntity> queryDecisionInstancesEntities(
      final DecisionInstanceListRequestDto request, final DecisionInstanceListResponseDto result) {
    final QueryBuilder query = createRequestQuery(request.getQuery());

    logger.debug("Decision instance search request: \n{}", query.toString());

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(query)
        .fetchSource(null, new String[]{RESULT, EVALUATED_INPUTS, EVALUATED_OUTPUTS});

    applySorting(searchSourceBuilder, request);

    SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(decisionInstanceTemplate)
        .source(searchSourceBuilder);

    logger.debug("Search request will search in: \n{}", searchRequest.indices());

    try {
      SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      result.setTotalCount(response.getHits().getTotalHits().value);

      List<DecisionInstanceEntity> decisionInstanceEntities = ElasticsearchUtil.mapSearchHits(response.getHits().getHits(),
          (sh) -> {
            DecisionInstanceEntity entity = ElasticsearchUtil.fromSearchHit(sh.getSourceAsString(), objectMapper, DecisionInstanceEntity.class);
            entity.setSortValues(sh.getSortValues());
            return entity;
          });
      if (request.getSearchBefore() != null) {
        Collections.reverse(decisionInstanceEntities);
      }
      return decisionInstanceEntities;
    } catch (IOException e) {
      final String message = String
          .format("Exception occurred, while obtaining instances list: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private void applySorting(SearchSourceBuilder searchSourceBuilder, DecisionInstanceListRequestDto request) {

    String sortBy = getSortBy(request);

    final boolean directSorting = request.getSearchAfter() != null || request.getSearchBefore() == null;
    if (request.getSorting() != null) {
      SortBuilder sort1;
      SortOrder sort1DirectOrder = SortOrder.fromString(request.getSorting().getSortOrder());
      if (directSorting) {
        sort1 = SortBuilders.fieldSort(sortBy).order(sort1DirectOrder)
            .missing("_last");
      } else {
        sort1 = SortBuilders.fieldSort(sortBy)
            .order(reverseOrder(sort1DirectOrder)).missing("_first");
      }
      searchSourceBuilder.sort(sort1);
    }

    SortBuilder sort2;
    SortBuilder sort3;
    Object[] querySearchAfter;
    if (directSorting) { //this sorting is also the default one for 1st page
      sort2 = SortBuilders.fieldSort(KEY).order(SortOrder.ASC);
      sort3 = SortBuilders.fieldSort(EXECUTION_INDEX).order(SortOrder.ASC);
      querySearchAfter = request.getSearchAfter(objectMapper); //may be null
    } else { //searchBefore != null
      //reverse sorting
      sort2 = SortBuilders.fieldSort(KEY).order(SortOrder.DESC);
      sort3 = SortBuilders.fieldSort(EXECUTION_INDEX).order(SortOrder.DESC);
      querySearchAfter = request.getSearchBefore(objectMapper);
    }

    searchSourceBuilder
        .sort(sort2)
        .sort(sort3)
        .size(request.getPageSize());
    if (querySearchAfter != null) {
      searchSourceBuilder.searchAfter(querySearchAfter);
    }
  }

  private String getSortBy(final DecisionInstanceListRequestDto request) {
    if (request.getSorting() != null) {
      String sortBy = request.getSorting().getSortBy();
      if (sortBy.equals(DecisionInstanceTemplate.ID)) {
        //we sort by id as numbers, not as strings
        sortBy = KEY;
      } else if (sortBy.equals(SORT_BY_PROCESS_INSTANCE_ID)) {
        sortBy = PROCESS_INSTANCE_KEY;
      }
      return sortBy;
    }
    return null;
  }

  private SortOrder reverseOrder(final SortOrder sortOrder) {
    if (sortOrder.equals(SortOrder.ASC)) {
      return SortOrder.DESC;
    } else {
      return SortOrder.ASC;
    }
  }

  private QueryBuilder createRequestQuery(final DecisionInstanceListQueryDto query) {
    QueryBuilder queryBuilder = joinWithAnd(
        createEvaluatedFailedQuery(query),
        createDecisionDefinitionIdsQuery(query),
        createIdsQuery(query),
        createProcessInstanceIdQuery(query),
        createEvaluationDateQuery(query),
        createReadPermissionQuery()
    );
    if (queryBuilder == null) {
      queryBuilder = matchAllQuery();
    }
    return queryBuilder;
  }

  private QueryBuilder createReadPermissionQuery() {
    return permissionsService == null ? null : permissionsService.createQueryForDecisionsByPermission(IdentityPermission.READ);
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
      //cover all instances
      return null;
    } else if (query.isFailed()) {
      return termQuery(STATE, FAILED);
    } else if (query.isEvaluated()) {
      return termQuery(STATE, EVALUATED);
    } else {
      return createMatchNoneQuery();
    }
  }

  public Map<String, List<DRDDataEntryDto>> getDecisionInstanceDRDData(String decisionInstanceId) {
    //we need to find all decision instances with he same key, which we extract from decisionInstanceId
    final Long decisionInstanceKey = DecisionInstanceEntity.extractKey(decisionInstanceId);
    final SearchRequest request = ElasticsearchUtil.createSearchRequest(decisionInstanceTemplate)
        .source(
            new SearchSourceBuilder()
                .query(termQuery(KEY, decisionInstanceKey))
                .fetchSource(new String[]{DECISION_ID, STATE}, null)
            .sort(EVALUATION_DATE, SortOrder.ASC)
        );
    try {
      final List<DRDDataEntryDto> entries = ElasticsearchUtil
          .scroll(request, DRDDataEntryDto.class, objectMapper, esClient,
              sh -> {
                final Map<String, Object> map = sh.getSourceAsMap();
                return new DRDDataEntryDto(sh.getId(), (String) map.get(DECISION_ID),
                    DecisionInstanceState.valueOf((String) map.get(STATE)));
              }, null, null);
      return entries.stream().collect(groupingBy(DRDDataEntryDto::getDecisionId));
    } catch (IOException e) {
      throw new OperateRuntimeException(
          "Exception occurred while quiering DRD data for decision instance id: "
              + decisionInstanceId);
    }
  }
}
