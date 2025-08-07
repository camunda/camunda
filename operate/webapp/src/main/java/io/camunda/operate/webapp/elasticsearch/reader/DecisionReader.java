/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.webapps.schema.descriptors.index.DecisionIndex.DECISION_REQUIREMENTS_KEY;
import static io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex.XML;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.dto.DecisionRequestDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
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
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class DecisionReader extends AbstractReader
    implements io.camunda.operate.webapp.reader.DecisionReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(DecisionReader.class);

  @Autowired private DecisionIndex decisionIndex;

  @Autowired private DecisionRequirementsIndex decisionRequirementsIndex;

  @Autowired private PermissionsService permissionsService;

  @Autowired private SecurityConfiguration securityConfiguration;

  private DecisionDefinitionEntity fromSearchHit(final String processString) {
    return ElasticsearchUtil.fromSearchHit(
        processString, objectMapper, DecisionDefinitionEntity.class);
  }

  /**
   * Gets the DMN diagram XML as a string.
   *
   * @param decisionDefinitionKey
   * @return
   */
  @Override
  public String getDiagram(final Long decisionDefinitionKey) {
    // get decisionRequirementsId
    SearchRequest searchRequest =
        new SearchRequest(decisionIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(idsQuery().addIds(decisionDefinitionKey.toString())));
    try {
      SearchResponse response = tenantAwareClient.search(searchRequest);
      if (response.getHits().getTotalHits().value == 0) {
        throw new NotFoundException("No decision definition found for id " + decisionDefinitionKey);
      }
      final Object key =
          response.getHits().getHits()[0].getSourceAsMap().get(DECISION_REQUIREMENTS_KEY);
      // key is either Integer or Long depending on value
      final Long decisionRequirementsId = Long.valueOf(String.valueOf(key));

      // get XML
      searchRequest =
          new SearchRequest(decisionRequirementsIndex.getAlias())
              .source(
                  new SearchSourceBuilder()
                      .query(idsQuery().addIds(String.valueOf(decisionRequirementsId)))
                      .fetchSource(XML, null));

      response = tenantAwareClient.search(searchRequest);

      if (response.getHits().getTotalHits().value == 1) {
        final Map<String, Object> result = response.getHits().getHits()[0].getSourceAsMap();
        return (String) result.get(XML);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(
            String.format("Could not find unique DRD with id '%s'.", decisionRequirementsId));
      } else {
        throw new NotFoundException(
            String.format("Could not find DRD with id '%s'.", decisionRequirementsId));
      }
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining the decision diagram: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  /**
   * Gets the decision by key
   *
   * @param decisionDefinitionKey decisionDefinitionKey
   * @return decision
   */
  @Override
  public DecisionDefinitionEntity getDecision(final Long decisionDefinitionKey) {
    final SearchRequest searchRequest =
        new SearchRequest(decisionIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(termQuery(DecisionIndex.KEY, decisionDefinitionKey)));
    try {
      final SearchResponse response = tenantAwareClient.search(searchRequest);
      if (response.getHits().getTotalHits().value == 1) {
        return fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(
            String.format("Could not find unique decision with key '%s'.", decisionDefinitionKey));
      } else {
        throw new NotFoundException(
            String.format("Could not find decision with key '%s'.", decisionDefinitionKey));
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the decision: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  /**
   * Returns map of Decision entities grouped by decisionId.
   *
   * @return
   */
  @Override
  public Map<String, List<DecisionDefinitionEntity>> getDecisionsGrouped(
      final DecisionRequestDto request) {
    final String tenantsGroupsAggName = "group_by_tenantId";
    final String groupsAggName = "group_by_decisionId";
    final String decisionsAggName = "decisions";

    final AggregationBuilder agg =
        terms(tenantsGroupsAggName)
            .field(DecisionIndex.TENANT_ID)
            .size(ElasticsearchUtil.TERMS_AGG_SIZE)
            .subAggregation(
                terms(groupsAggName)
                    .field(DecisionIndex.DECISION_ID)
                    .size(ElasticsearchUtil.TERMS_AGG_SIZE)
                    .subAggregation(
                        topHits(decisionsAggName)
                            .fetchSource(
                                new String[] {
                                  DecisionIndex.ID,
                                  DecisionIndex.NAME,
                                  DecisionIndex.VERSION,
                                  DecisionIndex.DECISION_ID,
                                  DecisionIndex.TENANT_ID
                                },
                                null)
                            .size(ElasticsearchUtil.TOPHITS_AGG_SIZE)
                            .sort(DecisionIndex.VERSION, SortOrder.DESC)));

    final SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().aggregation(agg).size(0);

    sourceBuilder.query(buildQuery(request.getTenantId()));

    final SearchRequest searchRequest =
        new SearchRequest(decisionIndex.getAlias()).source(sourceBuilder);

    try {
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      final Terms groups = searchResponse.getAggregations().get(tenantsGroupsAggName);
      final Map<String, List<DecisionDefinitionEntity>> result = new HashMap<>();

      groups.getBuckets().stream()
          .forEach(
              b -> {
                final String groupTenantId = b.getKeyAsString();
                final Terms decisionGroups = b.getAggregations().get(groupsAggName);

                decisionGroups.getBuckets().stream()
                    .forEach(
                        tenantB -> {
                          final String decisionId = tenantB.getKeyAsString();
                          final String groupKey = groupTenantId + "_" + decisionId;
                          result.put(groupKey, new ArrayList<>());

                          final TopHits processes = tenantB.getAggregations().get(decisionsAggName);
                          final SearchHit[] hits = processes.getHits().getHits();
                          for (final SearchHit searchHit : hits) {
                            final DecisionDefinitionEntity decisionEntity =
                                fromSearchHit(searchHit.getSourceAsString());
                            result.get(groupKey).add(decisionEntity);
                          }
                        });
              });

      return result;
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining grouped processes: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private QueryBuilder buildQuery(final String tenantId) {
    final var allowed =
        permissionsService.getDecisionsWithPermission(PermissionType.READ_DECISION_DEFINITION);
    final QueryBuilder decisionIdQ =
        allowed.isAll()
            ? matchAllQuery()
            : QueryBuilders.termsQuery(DecisionIndex.DECISION_ID, allowed.getIds());

    QueryBuilder tenantIdQ = null;
    if (securityConfiguration.getMultiTenancy().isChecksEnabled()) {
      tenantIdQ = tenantId != null ? termQuery(DecisionIndex.TENANT_ID, tenantId) : null;
    }
    return joinWithAnd(decisionIdQ, tenantIdQ);
  }
}
