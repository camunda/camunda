/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.es.reader;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentErrorTypeDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentFlowNodeDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentResponseDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.util.CollectionUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IncidentReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(IncidentReader.class);

  @Autowired
  private IncidentTemplate incidentTemplate;

  @Autowired
  private OperationReader operationReader;

  @Autowired
  private OperateProperties operateProperties;

  public List<IncidentEntity> getAllIncidentsByProcessInstanceKey(Long processInstanceKey) {
    final TermQueryBuilder processInstanceKeyQuery = termQuery(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);

    final ConstantScoreQueryBuilder query = constantScoreQuery(processInstanceKeyQuery);

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
        .source(new SearchSourceBuilder().query(query).sort(IncidentTemplate.CREATION_TIME, SortOrder.ASC));

    try {
      return scroll(searchRequest, IncidentEntity.class);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining all incidents: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  /**
   * Returns map of incident ids per process instance id.
   * @param processInstanceKeys
   * @return
   */
  public Map<Long, List<Long>> getIncidentKeysPerProcessInstance(List<Long> processInstanceKeys) {
    final QueryBuilder processInstanceKeysQuery = constantScoreQuery(termsQuery(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceKeys));
    final int batchSize = operateProperties.getElasticsearch().getBatchSize();

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
        .source(new SearchSourceBuilder()
            .query(processInstanceKeysQuery)
            .fetchSource(IncidentTemplate.PROCESS_INSTANCE_KEY, null)
            .size(batchSize));

    Map<Long, List<Long>> result = new HashMap<>();
    try {
      ElasticsearchUtil.scrollWith(searchRequest, esClient, searchHits -> {
        for (SearchHit hit : searchHits.getHits()) {
          CollectionUtil.addToMap(result, Long.valueOf(hit.getSourceAsMap().get(IncidentTemplate.PROCESS_INSTANCE_KEY).toString()), Long.valueOf(hit.getId()));
        }
      }, null, null);
      return result;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining all incidents: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  public IncidentEntity getIncidentById(Long incidentKey) {
    final IdsQueryBuilder idsQ = idsQuery().addIds(incidentKey.toString());

    final ConstantScoreQueryBuilder query = constantScoreQuery(idsQ);

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
        .source(new SearchSourceBuilder().query(query));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value == 1) {
        return ElasticsearchUtil.fromSearchHit(response.getHits().getHits()[0].getSourceAsString(), objectMapper, IncidentEntity.class);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(String.format("Could not find unique incident with key '%s'.", incidentKey));
      } else {
        throw new NotFoundException(String.format("Could not find incident with key '%s'.", incidentKey));
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining incident: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }

  }

  public IncidentResponseDto getIncidentsByProcessInstanceKey(Long processInstanceKey) {
    final TermQueryBuilder processInstanceQuery = termQuery(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);

    final String errorTypesAggName = "errorTypesAgg";
    final String flowNodesAggName = "flowNodesAgg";

    final TermsAggregationBuilder errorTypesAgg = terms(errorTypesAggName).field(IncidentTemplate.ERROR_TYPE).size(
        ErrorType.values().length)
        .order(BucketOrder.key(true));
    final TermsAggregationBuilder flowNodesAgg = terms(flowNodesAggName).field(IncidentTemplate.FLOW_NODE_ID).size(ElasticsearchUtil.TERMS_AGG_SIZE)
        .order(BucketOrder.key(true));

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
        .source(new SearchSourceBuilder().query(constantScoreQuery(processInstanceQuery)).aggregation(errorTypesAgg).aggregation(flowNodesAgg));

    IncidentResponseDto incidentResponse = new IncidentResponseDto();
    try {
      final List<IncidentEntity> incidents = scroll(searchRequest, IncidentEntity.class, aggs -> {
        ((Terms) aggs.get(errorTypesAggName)).getBuckets().forEach(b -> {
          ErrorType errorType = ErrorType.valueOf(b.getKeyAsString());
          incidentResponse.getErrorTypes().add(new IncidentErrorTypeDto(errorType.getTitle(), (int) b.getDocCount()));
        });
        ((Terms) aggs.get(flowNodesAggName)).getBuckets()
            .forEach(b -> incidentResponse.getFlowNodes().add(new IncidentFlowNodeDto(b.getKeyAsString(), (int) b.getDocCount())));
      });

      final Map<Long, List<OperationEntity>> operations = operationReader.getOperationsPerIncidentKey(processInstanceKey);

      incidentResponse.setIncidents(IncidentDto.sortDefault(IncidentDto.createFrom(incidents, operations)));
      incidentResponse.setCount(incidents.size());

      return incidentResponse;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining incidents: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

}
