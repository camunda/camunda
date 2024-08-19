/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_DELETED;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static java.util.stream.Collectors.toSet;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.reader.DefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessDefinitionReaderES implements ProcessDefinitionReader {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ProcessDefinitionReaderES.class);
  private final DefinitionReaderES definitionReader;
  private final OptimizeElasticsearchClient esClient;

  public ProcessDefinitionReaderES(
      final DefinitionReaderES definitionReader, final OptimizeElasticsearchClient esClient) {
    this.definitionReader = definitionReader;
    this.esClient = esClient;
  }

  @Override
  public Optional<ProcessDefinitionOptimizeDto> getProcessDefinition(final String definitionId) {
    final BoolQueryBuilder query = boolQuery().must(matchAllQuery());
    query.must(termsQuery(PROCESS_DEFINITION_ID, definitionId));
    return definitionReader.getDefinitions(DefinitionType.PROCESS, query, true).stream()
        .findFirst()
        .map(ProcessDefinitionOptimizeDto.class::cast);
  }

  @Override
  public Set<String> getAllNonOnboardedProcessDefinitionKeys() {
    final String defKeyAgg = "keyAgg";
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(
                boolQuery()
                    .must(termsQuery(ProcessDefinitionIndex.ONBOARDED, false))
                    .must(termQuery(DEFINITION_DELETED, false))
                    .should(existsQuery(PROCESS_DEFINITION_XML)))
            .aggregation(terms(defKeyAgg).field(ProcessDefinitionIndex.PROCESS_DEFINITION_KEY))
            .fetchSource(false)
            .size(MAX_RESPONSE_SIZE_LIMIT);
    final SearchRequest searchRequest =
        new SearchRequest(PROCESS_DEFINITION_INDEX_NAME).source(searchSourceBuilder);

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (final IOException e) {
      final String reason = "Was not able to fetch non-onboarded process definition keys.";
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    final Terms definitionKeyTerms = searchResponse.getAggregations().get(defKeyAgg);
    return definitionKeyTerms.getBuckets().stream()
        .map(MultiBucketsAggregation.Bucket::getKeyAsString)
        .collect(toSet());
  }

  @Override
  public DefinitionReader getDefinitionReader() {
    return definitionReader;
  }
}
