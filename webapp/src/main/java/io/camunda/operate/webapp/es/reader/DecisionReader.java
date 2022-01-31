/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.es.reader;

import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;

import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.TopHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DecisionReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(DecisionReader.class);

  @Autowired
  private DecisionIndex decisionIndex;

  @Autowired
  private DecisionRequirementsIndex decisionRequirementsIndex;

  private DecisionDefinitionEntity fromSearchHit(String processString) {
    return ElasticsearchUtil
        .fromSearchHit(processString, objectMapper, DecisionDefinitionEntity.class);
  }

  /**
   * Returns map of Decision entities grouped by decisionId.
   * @return
   */
  public Map<String, List<DecisionDefinitionEntity>> getDecisionsGrouped() {
    final String groupsAggName = "group_by_decisionId";
    final String decisionsAggName = "decisions";

    AggregationBuilder agg =
      terms(groupsAggName)
        .field(DecisionIndex.DECISION_ID)
        .size(ElasticsearchUtil.TERMS_AGG_SIZE)
        .subAggregation(
          topHits(decisionsAggName)
            .fetchSource(new String[] { DecisionIndex.ID, DecisionIndex.NAME, DecisionIndex.VERSION, DecisionIndex.DECISION_ID  }, null)
            .size(ElasticsearchUtil.TOPHITS_AGG_SIZE)
            .sort(DecisionIndex.VERSION, SortOrder.DESC));

    final SearchRequest searchRequest = new SearchRequest(decisionIndex.getAlias())
      .source(new SearchSourceBuilder()
        .aggregation(agg)
        .size(0));

    try {
      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final Terms groups = searchResponse.getAggregations().get(groupsAggName);
      Map<String, List<DecisionDefinitionEntity>> result = new HashMap<>();

      groups.getBuckets().stream().forEach(b -> {
        final String decisionId = b.getKeyAsString();
        result.put(decisionId, new ArrayList<>());

        final TopHits decisions = b.getAggregations().get(decisionsAggName);
        final SearchHit[] hits = decisions.getHits().getHits();
        for (SearchHit searchHit: hits) {
          final DecisionDefinitionEntity decisionEntity = fromSearchHit(searchHit.getSourceAsString());
          result.get(decisionId).add(decisionEntity);
        }
      });

      return result;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining grouped processes: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

}
