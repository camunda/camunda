/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;
import static java.util.stream.Collectors.toSet;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.reader.DefinitionInstanceReader;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
@Conditional(ElasticSearchCondition.class)
public class DefinitionInstanceReaderES extends DefinitionInstanceReader {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(DefinitionInstanceReaderES.class);
  private final OptimizeElasticsearchClient esClient;

  public DefinitionInstanceReaderES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  public Set<String> getAllExistingDefinitionKeys(
      final DefinitionType type, final Set<String> instanceIds) {
    final BoolQueryBuilder idQuery =
        CollectionUtils.isEmpty(instanceIds)
            ? boolQuery().must(matchAllQuery())
            : boolQuery().filter(termsQuery(resolveInstanceIdFieldForType(type), instanceIds));
    final String defKeyAggName = "definitionKeyAggregation";
    final TermsAggregationBuilder definitionKeyAgg =
        AggregationBuilders.terms(defKeyAggName)
            .field(resolveDefinitionKeyFieldForType(type))
            .size(MAX_RESPONSE_SIZE_LIMIT);
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(idQuery).fetchSource(false).size(0);
    searchSourceBuilder.aggregation(definitionKeyAgg);

    final SearchRequest searchRequest =
        new SearchRequest(resolveIndexMultiAliasForType(type)).source(searchSourceBuilder);

    final SearchResponse response;
    try {
      response = esClient.search(searchRequest);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          String.format("Was not able to retrieve definition keys for instances of type %s", type),
          e);
    } catch (final ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(type, e)) {
        log.info(
            "Was not able to retrieve definition keys for instances because no {} instance indices exist. "
                + "Returning empty set.",
            type);
        return Collections.emptySet();
      }
      throw e;
    }

    final Terms definitionKeyTerms = response.getAggregations().get(defKeyAggName);
    return definitionKeyTerms.getBuckets().stream()
        .map(MultiBucketsAggregation.Bucket::getKeyAsString)
        .collect(toSet());
  }
}
