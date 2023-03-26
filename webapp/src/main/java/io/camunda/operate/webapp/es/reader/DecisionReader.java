/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.es.reader;

import static io.camunda.operate.schema.indices.DecisionIndex.DECISION_REQUIREMENTS_KEY;
import static io.camunda.operate.schema.indices.DecisionRequirementsIndex.XML;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;

import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilders;
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

  @Autowired(required = false)
  private PermissionsService permissionsService;

  private DecisionDefinitionEntity fromSearchHit(String processString) {
    return ElasticsearchUtil
        .fromSearchHit(processString, objectMapper, DecisionDefinitionEntity.class);
  }

  /**
   * Gets the DMN diagram XML as a string.
   * @param decisionDefinitionId
   * @return
   */
  public String getDiagram(String decisionDefinitionId) {
    //get decisionRequirementsId
    SearchRequest searchRequest = new SearchRequest(decisionIndex.getAlias())
        .source(new SearchSourceBuilder().query(idsQuery().addIds(decisionDefinitionId)));
    try {
      SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value == 0) {
        throw new NotFoundException(
            "No decision definition found for id " + decisionDefinitionId);
      }
      final Object key = response.getHits().getHits()[0].getSourceAsMap()
          .get(DECISION_REQUIREMENTS_KEY);
      //key is either Integer or Long depending on value
      final Long decisionRequirementsId = Long.valueOf(String.valueOf(key));

      //get XML
      searchRequest = new SearchRequest(decisionRequirementsIndex.getAlias())
          .source(new SearchSourceBuilder()
              .query(idsQuery().addIds(String.valueOf(decisionRequirementsId)))
              .fetchSource(XML, null));

      response = esClient.search(searchRequest, RequestOptions.DEFAULT);

      if (response.getHits().getTotalHits().value == 1) {
        Map<String, Object> result = response.getHits().getHits()[0].getSourceAsMap();
        return (String) result.get(XML);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(
            String.format("Could not find unique DRD with id '%s'.", decisionRequirementsId));
      } else {
        throw new NotFoundException(
            String.format("Could not find DRD with id '%s'.", decisionRequirementsId));
      }
    } catch (IOException e) {
      final String message = String
          .format("Exception occurred, while obtaining the decision diagram: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  /**
   * Gets the decision by key
   * @param decisionDefinitionKey decisionDefinitionKey
   * @return decision
   */
  public DecisionDefinitionEntity getDecision(Long decisionDefinitionKey) {
    final SearchRequest searchRequest = new SearchRequest(decisionIndex.getAlias())
        .source(new SearchSourceBuilder()
            .query(QueryBuilders.termQuery(DecisionIndex.KEY, decisionDefinitionKey)));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value == 1) {
        return fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(String.format("Could not find unique decision with key '%s'.", decisionDefinitionKey));
      } else {
        throw new NotFoundException(String.format("Could not find decision with key '%s'.", decisionDefinitionKey));
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining the decision: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
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

    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
        .aggregation(agg)
        .size(0);
    if(permissionsService != null) {
      sourceBuilder.query(permissionsService.createQueryForDecisionsByPermission(IdentityPermission.READ));
    }
    final SearchRequest searchRequest = new SearchRequest(decisionIndex.getAlias()).source(sourceBuilder);

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
