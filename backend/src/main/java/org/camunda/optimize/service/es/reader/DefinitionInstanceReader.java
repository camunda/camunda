/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@AllArgsConstructor
@Component
@Slf4j
public class DefinitionInstanceReader {
  private final OptimizeElasticsearchClient esClient;

  public Set<String> getAllExistingDefinitionKeys(final DefinitionType type) {
    return getAllExistingDefinitionKeys(type, Collections.emptySet());
  }

  public Set<String> getAllExistingDefinitionKeys(final DefinitionType type,
                                                  final Set<String> instanceIds) {
    final BoolQueryBuilder idQuery = CollectionUtils.isEmpty(instanceIds)
      ? boolQuery().must(matchAllQuery())
      : boolQuery().filter(termsQuery(resolveInstanceIdFieldForType(type), instanceIds));
    final String defKeyAggName = "definitionKeyAggregation";
    final TermsAggregationBuilder definitionKeyAgg = AggregationBuilders
      .terms(defKeyAggName)
      .field(resolveDefinitionKeyFieldForType(type))
      .size(MAX_RESPONSE_SIZE_LIMIT);
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(idQuery).fetchSource(false).size(0);
    searchSourceBuilder.aggregation(definitionKeyAgg);

    final SearchRequest searchRequest =
      new SearchRequest(resolveIndexMultiAliasForType(type)).source(searchSourceBuilder);

    final SearchResponse response;
    try {
      response = esClient.search(searchRequest);
    } catch (IOException e) {
      throw new OptimizeRuntimeException(String.format(
        "Was not able to retrieve definition keys for instances of type %s",
        type
      ), e);
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(type, e)) {
        log.info(
          "Was not able to retrieve definition keys for instances because no {} instance indices exist. " +
            "Returning empty set.",
          type
        );
        return Collections.emptySet();
      }
      throw e;
    }

    final Terms definitionKeyTerms = response.getAggregations().get(defKeyAggName);
    return definitionKeyTerms.getBuckets().stream()
      .map(MultiBucketsAggregation.Bucket::getKeyAsString)
      .collect(toSet());
  }

  private String[] resolveIndexMultiAliasForType(final DefinitionType type) {
    switch (type) {
      case PROCESS:
        return new String[]{PROCESS_INSTANCE_MULTI_ALIAS};
      case DECISION:
        return new String[]{DECISION_INSTANCE_MULTI_ALIAS};
      default:
        throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    }
  }

  private String resolveDefinitionKeyFieldForType(final DefinitionType type) {
    switch (type) {
      case PROCESS:
        return ProcessInstanceDto.Fields.processDefinitionKey;
      case DECISION:
        return DecisionInstanceDto.Fields.decisionDefinitionKey;
      default:
        throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    }
  }

  private String resolveInstanceIdFieldForType(final DefinitionType type) {
    switch (type) {
      case PROCESS:
        return ProcessInstanceDto.Fields.processInstanceId;
      case DECISION:
        return DecisionInstanceDto.Fields.decisionInstanceId;
      default:
        throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    }
  }
}
