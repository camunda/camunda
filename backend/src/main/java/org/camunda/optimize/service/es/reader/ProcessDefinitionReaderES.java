/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.db.reader.DefinitionReader;
import org.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.service.es.schema.index.AbstractDefinitionIndex.DEFINITION_DELETED;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class ProcessDefinitionReaderES implements ProcessDefinitionReader {

  private final DefinitionReader definitionReader;
  private final OptimizeElasticsearchClient esClient;

  @Override
  public List<ProcessDefinitionOptimizeDto> getAllProcessDefinitions() {
    return definitionReader.getDefinitions(DefinitionType.PROCESS, false, false, true);
  }

  @Override
  public Optional<ProcessDefinitionOptimizeDto> getProcessDefinition(final String definitionId) {
    final BoolQueryBuilder query = boolQuery().must(matchAllQuery());
    query.must(termsQuery(PROCESS_DEFINITION_ID, definitionId));
    return definitionReader.getDefinitions(DefinitionType.PROCESS, query, true)
      .stream()
      .findFirst()
      .map(ProcessDefinitionOptimizeDto.class::cast);
  }

  @Override
  public Set<String> getAllNonOnboardedProcessDefinitionKeys() {
    final String defKeyAgg = "keyAgg";
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(boolQuery()
               .must(termsQuery(ProcessDefinitionIndex.ONBOARDED, false))
               .must(termQuery(DEFINITION_DELETED, false))
               .should(existsQuery(PROCESS_DEFINITION_XML))
      )
      .aggregation(terms(defKeyAgg).field(ProcessDefinitionIndex.PROCESS_DEFINITION_KEY))
      .fetchSource(false)
      .size(MAX_RESPONSE_SIZE_LIMIT);
    SearchRequest searchRequest = new SearchRequest(PROCESS_DEFINITION_INDEX_NAME).source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      String reason = "Was not able to fetch non-onboarded process definition keys.";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    final Terms definitionKeyTerms = searchResponse.getAggregations().get(defKeyAgg);
    return definitionKeyTerms.getBuckets().stream()
      .map(MultiBucketsAggregation.Bucket::getKeyAsString)
      .collect(toSet());
  }

  @Override
  public String getLatestVersionToKey(String key) {
    return definitionReader.getLatestVersionToKey(DefinitionType.PROCESS, key);
  }

}
