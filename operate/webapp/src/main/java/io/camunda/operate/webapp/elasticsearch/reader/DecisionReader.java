/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.TopHitsAggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.dto.DecisionRequestDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  @Autowired private PermissionsService permissionsService;

  @Autowired private SecurityConfiguration securityConfiguration;

  /**
   * Gets the decision by key
   *
   * @param decisionDefinitionKey decisionDefinitionKey
   * @return decision
   */
  @Override
  public DecisionDefinitionEntity getDecision(final Long decisionDefinitionKey) {
    final var query = ElasticsearchUtil.termsQuery(DecisionIndex.KEY, decisionDefinitionKey);
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);
    final var searchRequest =
        new SearchRequest.Builder().index(decisionIndex.getAlias()).query(tenantAwareQuery).build();

    try {
      final var response = esClient.search(searchRequest, DecisionDefinitionEntity.class);
      final var totalHits = response.hits().total().value();
      if (totalHits == 1) {
        final var hit = response.hits().hits().get(0);
        final var source = hit.source();
        if (source == null) {
          throw new OperateRuntimeException(
              String.format(
                  "Decision source is missing for key '%s' despite a search hit being returned.",
                  decisionDefinitionKey));
        }
        return source;
      } else if (totalHits > 1) {
        throw new NotFoundException(
            String.format("Could not find unique decision with key '%s'.", decisionDefinitionKey));
      } else {
        throw new NotFoundException(
            String.format("Could not find decision with key '%s'.", decisionDefinitionKey));
      }
    } catch (final IOException e) {
      final var message =
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
    final var tenantsGroupsAggName = "group_by_tenantId";
    final var groupsAggName = "group_by_decisionId";
    final var decisionsAggName = "decisions";

    final var decisionsAgg =
        new TopHitsAggregation.Builder()
            .source(
                s ->
                    s.filter(
                        f ->
                            f.includes(
                                DecisionIndex.ID,
                                DecisionIndex.NAME,
                                DecisionIndex.VERSION,
                                DecisionIndex.DECISION_ID,
                                DecisionIndex.TENANT_ID)))
            .size(ElasticsearchUtil.TOPHITS_AGG_SIZE)
            .sort(s -> s.field(f -> f.field(DecisionIndex.VERSION).order(SortOrder.Desc)))
            .build();

    final var groupByDecisionIdAgg =
        new Aggregation.Builder()
            .terms(t -> t.field(DecisionIndex.DECISION_ID).size(ElasticsearchUtil.TERMS_AGG_SIZE))
            .aggregations(Map.of(decisionsAggName, decisionsAgg._toAggregation()))
            .build();

    final var groupByTenantIdAgg =
        new Aggregation.Builder()
            .terms(t -> t.field(DecisionIndex.TENANT_ID).size(ElasticsearchUtil.TERMS_AGG_SIZE))
            .aggregations(Map.of(groupsAggName, groupByDecisionIdAgg))
            .build();

    final var query = buildQuery(request.getTenantId());
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);
    final var searchRequest =
        new SearchRequest.Builder()
            .index(decisionIndex.getAlias())
            .query(tenantAwareQuery)
            .aggregations(tenantsGroupsAggName, groupByTenantIdAgg)
            .size(0)
            .build();

    try {
      final var searchResponse = esClient.search(searchRequest, DecisionDefinitionEntity.class);
      final var groups = searchResponse.aggregations().get(tenantsGroupsAggName).sterms();
      final var result = new HashMap<String, List<DecisionDefinitionEntity>>();

      groups.buckets().array().stream()
          .forEach(
              b -> {
                final var groupTenantId = b.key().stringValue();
                final StringTermsAggregate decisionGroups =
                    b.aggregations().get(groupsAggName).sterms();

                decisionGroups.buckets().array().stream()
                    .forEach(
                        tenantB -> {
                          final var decisionId = tenantB.key().stringValue();
                          final var groupKey = groupTenantId + "_" + decisionId;

                          final var decisionHits =
                              tenantB.aggregations().get(decisionsAggName).topHits();
                          final List<DecisionDefinitionEntity> decisions =
                              decisionHits.hits().hits().stream()
                                  .map(Hit::source)
                                  .filter(Objects::nonNull)
                                  .map(source -> source.to(DecisionDefinitionEntity.class))
                                  .toList();
                          result.put(groupKey, new ArrayList<>(decisions));
                        });
              });

      return result;
    } catch (final IOException e) {
      final var message =
          String.format(
              "Exception occurred, while obtaining grouped decisions: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private Query buildQuery(final String tenantId) {
    final var allowed =
        permissionsService.getDecisionsWithPermission(PermissionType.READ_DECISION_DEFINITION);

    final var decisionIdQ =
        allowed.isAll()
            ? ElasticsearchUtil.matchAllQuery()
            : ElasticsearchUtil.termsQuery(DecisionIndex.DECISION_ID, allowed.getIds());

    Query tenantIdQ = null;
    if (securityConfiguration.getMultiTenancy().isChecksEnabled()) {
      tenantIdQ =
          tenantId != null ? ElasticsearchUtil.termsQuery(DecisionIndex.TENANT_ID, tenantId) : null;
    }
    return ElasticsearchUtil.joinWithAnd(decisionIdQ, tenantIdQ);
  }
}
