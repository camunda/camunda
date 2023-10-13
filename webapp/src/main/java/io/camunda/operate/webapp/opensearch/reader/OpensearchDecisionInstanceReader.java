/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.opensearch.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.entities.dmn.DecisionInstanceState;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.webapp.reader.DecisionInstanceReader;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import io.camunda.operate.webapp.rest.dto.dmn.DRDDataEntryDto;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceForListDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListQueryDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListRequestDto;
import io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListResponseDto;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.camunda.operate.entities.dmn.DecisionInstanceState.EVALUATED;
import static io.camunda.operate.entities.dmn.DecisionInstanceState.FAILED;
import static io.camunda.operate.schema.templates.DecisionInstanceTemplate.*;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.CollectionUtil.toSafeListOfStrings;
import static io.camunda.operate.webapp.rest.dto.dmn.list.DecisionInstanceListRequestDto.SORT_BY_PROCESS_INSTANCE_ID;
import static java.util.stream.Collectors.groupingBy;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchDecisionInstanceReader implements DecisionInstanceReader {

  private static final Logger logger = LoggerFactory.getLogger(OpensearchDecisionInstanceReader.class);

  @Autowired
  private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired
  private DateTimeFormatter dateTimeFormatter;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired(required = false)
  private PermissionsService permissionsService;

  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

  @Override
  public DecisionInstanceDto getDecisionInstance(String decisionInstanceId) {
    var searchRequest = searchRequestBuilder(decisionInstanceTemplate)
        .query(withTenantCheck(constantScore(
            and(
                ids(decisionInstanceId),
                term(DecisionInstanceTemplate.ID, decisionInstanceId))
            )));

    return DtoCreator.create(
        richOpenSearchClient.doc().searchUnique(searchRequest, DecisionInstanceEntity.class, decisionInstanceId),
        DecisionInstanceDto.class);
  }

  @Override
  public DecisionInstanceListResponseDto queryDecisionInstances(DecisionInstanceListRequestDto request) {
    DecisionInstanceListResponseDto result = new DecisionInstanceListResponseDto();
    List<DecisionInstanceEntity> entities = queryDecisionInstancesEntities(request, result);
    result.setDecisionInstances(DecisionInstanceForListDto.createFrom(entities, objectMapper));
    return result;
  }

  private List<DecisionInstanceEntity> queryDecisionInstancesEntities(final DecisionInstanceListRequestDto request, final DecisionInstanceListResponseDto result) {
    var query = createRequestQuery(request.getQuery());

    logger.debug("Decision instance search request: \n{}", query.toString());

    var searchRequest = searchRequestBuilder(decisionInstanceTemplate)
        .query(query)
        .source(sourceExclude(RESULT, EVALUATED_INPUTS, EVALUATED_OUTPUTS));

    applySorting(searchRequest, request);

    logger.debug("Search request will search in: \n{}", decisionInstanceTemplate.getFullQualifiedName());

    var response = richOpenSearchClient.doc().search(searchRequest, DecisionInstanceEntity.class);
    result.setTotalCount(response.hits().total().value());

    final List<DecisionInstanceEntity> decisionInstanceEntities = new ArrayList<>(
        response.hits().hits().stream()
        .filter( hit -> hit.source() != null)
        .map(hit -> hit.source().setSortValues(hit.sort().toArray()))
        .toList()
    );
    if (request.getSearchBefore() != null) {
      Collections.reverse(decisionInstanceEntities);
    }
    return decisionInstanceEntities;
  }

  private Query createRequestQuery(final DecisionInstanceListQueryDto decisionInstanceListQueryDto) {
    var query = withTenantCheck(and(
        createEvaluatedFailedQuery(decisionInstanceListQueryDto),
        createDecisionDefinitionIdsQuery(decisionInstanceListQueryDto),
        createIdsQuery(decisionInstanceListQueryDto),
        createProcessInstanceIdQuery(decisionInstanceListQueryDto),
        createEvaluationDateQuery(decisionInstanceListQueryDto),
        createReadPermissionQuery(),
        createTenantIdQuery(decisionInstanceListQueryDto)
    ));
    return query == null? matchAll(): query;
  }

  private Query createTenantIdQuery(DecisionInstanceListQueryDto query) {
    if (query.getTenantId() != null) {
      return term(DecisionInstanceTemplate.TENANT_ID, query.getTenantId());
    }
    return null;
  }

  private Query createReadPermissionQuery() {
    if( permissionsService == null) return null;
    var allowed = permissionsService.getDecisionsWithPermission(IdentityPermission.READ);
    if (allowed == null) return null;
    return allowed.isAll() ? matchAll() : stringTerms(DecisionIndex.DECISION_ID, allowed.getIds());
  }

  private Query createEvaluationDateQuery(final DecisionInstanceListQueryDto decisionInstanceListQueryDto) {
    if (decisionInstanceListQueryDto.getEvaluationDateAfter() != null || decisionInstanceListQueryDto.getEvaluationDateBefore() != null) {
      var query = RangeQuery.of(q -> {
        q.field(EVALUATION_DATE);
        if (decisionInstanceListQueryDto.getEvaluationDateAfter() != null) {
          q.gte(JsonData.of(dateTimeFormatter.format(decisionInstanceListQueryDto.getEvaluationDateAfter())));
        }
        if (decisionInstanceListQueryDto.getEvaluationDateBefore() != null) {
          q.lt(JsonData.of(dateTimeFormatter.format(decisionInstanceListQueryDto.getEvaluationDateBefore())));
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
      //cover all instances
      return null;
    } else if (query.isFailed()) {
      return term(STATE, FAILED.name());
    } else if (query.isEvaluated()) {
      return term(STATE, EVALUATED.name());
    } else {
      return matchNone();
    }
  }


  private void applySorting(SearchRequest.Builder searchRequest, DecisionInstanceListRequestDto request) {
    String sortBy = getSortBy(request);

    final boolean directSorting = request.getSearchAfter() != null || request.getSearchBefore() == null;
    if (request.getSorting() != null) {
      SortOptions sort1;
      SortOrder sort1DirectOrder = request.getSorting().getSortOrder().equalsIgnoreCase("desc")?
          SortOrder.Desc:SortOrder.Asc;
      if (directSorting) {
        sort1 = sortOptions(sortBy, sort1DirectOrder, "_last");
      } else {
        sort1 = sortOptions(sortBy,reverseOrder(sort1DirectOrder),"_first");
      }
      searchRequest.sort(sort1);
    }

    SortOptions sort2;
    SortOptions sort3;
    Object[] querySearchAfter;
    if (directSorting) { //this sorting is also the default one for 1st page
      sort2 = sortOptions(KEY, SortOrder.Asc);
      sort3 = sortOptions(EXECUTION_INDEX, SortOrder.Asc);
      querySearchAfter = request.getSearchAfter(objectMapper); //may be null
    } else { //searchBefore != null
      //reverse sorting
      sort2 = sortOptions(KEY, SortOrder.Desc);
      sort3 = sortOptions(EXECUTION_INDEX, SortOrder.Desc);
      querySearchAfter = request.getSearchBefore(objectMapper);
    }

    searchRequest
        .sort(sort2)
        .sort(sort3)
        .size(request.getPageSize());
    if (querySearchAfter != null) {
      searchRequest.searchAfter(toSafeListOfStrings(querySearchAfter));
    }
  }

  private String getSortBy(final DecisionInstanceListRequestDto request) {
    if (request.getSorting() != null) {
      String sortBy = request.getSorting().getSortBy();
      if (sortBy.equals(DecisionInstanceTemplate.ID)) {
        //we sort by id as numbers, not as strings
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

  @Override
  public Map<String, List<DRDDataEntryDto>> getDecisionInstanceDRDData(String decisionInstanceId) {
    //we need to find all decision instances with the same key, which we extract from decisionInstanceId
    final Long decisionInstanceKey = DecisionInstanceEntity.extractKey(decisionInstanceId);

    var searchRequest = searchRequestBuilder(decisionInstanceTemplate)
        .query(withTenantCheck(term(DecisionInstanceTemplate.KEY, decisionInstanceKey)))
        .source(sourceInclude(DECISION_ID, STATE));

    List<DRDDataEntryDto> results = new ArrayList<>();
    richOpenSearchClient.doc().scrollWith(searchRequest, Map.class, hits -> hits.stream()
        .filter( hit -> hit.source() !=null)
        .forEach(hit -> {
      var map = hit.source();
      results.add(new DRDDataEntryDto(
          hit.id(),
          (String) map.get(DECISION_ID),
          DecisionInstanceState.valueOf((String) map.get(STATE)))
      );
    }));
    return results.stream().collect(groupingBy(DRDDataEntryDto::getDecisionId));
  }
}
