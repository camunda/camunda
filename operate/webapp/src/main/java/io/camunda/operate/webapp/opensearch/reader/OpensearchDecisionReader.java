/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations.TERMS_AGG_SIZE;
import static io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations.TOPHITS_AGG_SIZE;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.*;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.reader.DecisionReader;
import io.camunda.operate.webapp.rest.dto.DecisionRequestDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchDecisionReader implements DecisionReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchDecisionReader.class);

  @Autowired private DecisionIndex decisionIndex;

  @Autowired private DecisionRequirementsIndex decisionRequirementsIndex;

  @Autowired private PermissionsService permissionsService;

  @Autowired private SecurityConfiguration securityConfiguration;

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Override
  public String getDiagram(final Long decisionDefinitionKey) {
    record DecisionRequirementsIdRecord(Long decisionRequirementsKey) {}
    final var request =
        searchRequestBuilder(decisionIndex.getAlias())
            .query(withTenantCheck(ids(decisionDefinitionKey.toString())));
    final var hits =
        richOpenSearchClient.doc().search(request, DecisionRequirementsIdRecord.class).hits();
    if (hits.total().value() == 0) {
      throw new NotFoundException("No decision definition found for id " + decisionDefinitionKey);
    }
    final var decisionRequirementsId = hits.hits().get(0).source().decisionRequirementsKey;

    final var xmlRequest =
        searchRequestBuilder(decisionRequirementsIndex.getAlias())
            .query(withTenantCheck(ids(decisionRequirementsId.toString())))
            .source(sourceInclude(DecisionRequirementsIndex.XML));
    record XmlRecord(String xml) {}
    final var xmlHits = richOpenSearchClient.doc().search(xmlRequest, XmlRecord.class).hits();
    if (xmlHits.total().value() == 1) {
      return xmlHits.hits().get(0).source().xml;
    } else if (hits.total().value() > 1) {
      throw new NotFoundException(
          String.format("Could not find unique DRD with id '%s'.", decisionRequirementsId));
    } else {
      throw new NotFoundException(
          String.format("Could not find DRD with id '%s'.", decisionRequirementsId));
    }
  }

  @Override
  public DecisionDefinitionEntity getDecision(final Long decisionDefinitionKey) {
    final var request =
        searchRequestBuilder(decisionIndex.getAlias())
            .query(withTenantCheck(term(DecisionIndex.KEY, decisionDefinitionKey)));
    final var hits =
        richOpenSearchClient.doc().search(request, DecisionDefinitionEntity.class).hits();
    if (hits.total().value() == 1) {
      return hits.hits().get(0).source();
    } else if (hits.total().value() > 1) {
      throw new NotFoundException(
          String.format("Could not find unique decision with key '%s'.", decisionDefinitionKey));
    } else {
      throw new NotFoundException(
          String.format("Could not find decision with key '%s'.", decisionDefinitionKey));
    }
  }

  @Override
  public Map<String, List<DecisionDefinitionEntity>> getDecisionsGrouped(
      final DecisionRequestDto request) {
    final var tenantsGroupsAggName = "group_by_tenantId";
    final var groupsAggName = "group_by_decisionId";
    final var decisionsAggName = "decisions";
    final var sourceFields =
        List.of(
            DecisionIndex.ID,
            DecisionIndex.NAME,
            DecisionIndex.VERSION,
            DecisionIndex.DECISION_ID,
            DecisionIndex.TENANT_ID);

    final var aggregationsRequest =
        searchRequestBuilder(decisionIndex.getAlias())
            .query(withTenantCheck(buildQuery(request.getTenantId())))
            .size(0)
            .aggregations(
                tenantsGroupsAggName,
                withSubaggregations(
                    termAggregation(ProcessIndex.TENANT_ID, TERMS_AGG_SIZE),
                    Map.of(
                        groupsAggName,
                        withSubaggregations(
                            termAggregation(DecisionIndex.DECISION_ID, TERMS_AGG_SIZE),
                            Map.of(
                                decisionsAggName,
                                topHitsAggregation(
                                        sourceFields,
                                        TOPHITS_AGG_SIZE,
                                        sortOptions(DecisionIndex.VERSION, SortOrder.Desc))
                                    ._toAggregation())))));

    final Map<String, List<DecisionDefinitionEntity>> result = new HashMap<>();
    final var response = richOpenSearchClient.doc().search(aggregationsRequest, Object.class);

    response
        .aggregations()
        .get(tenantsGroupsAggName)
        .sterms()
        .buckets()
        .array()
        .forEach(
            tenantBucket ->
                tenantBucket
                    .aggregations()
                    .get(groupsAggName)
                    .sterms()
                    .buckets()
                    .array()
                    .forEach(
                        decisionIdBucket -> {
                          final String key = tenantBucket.key() + "_" + decisionIdBucket.key();
                          final List<DecisionDefinitionEntity> value =
                              decisionIdBucket
                                  .aggregations()
                                  .get(decisionsAggName)
                                  .topHits()
                                  .hits()
                                  .hits()
                                  .stream()
                                  .map(h -> h.source().to(DecisionDefinitionEntity.class))
                                  .toList();
                          result.put(key, value);
                        }));

    return result;
  }

  private Query buildQuery(final String tenantId) {
    final var allowed =
        permissionsService.getDecisionsWithPermission(PermissionType.READ_DECISION_DEFINITION);
    final Query decisionIdQuery =
        allowed.isAll() ? matchAll() : stringTerms(DecisionIndex.DECISION_ID, allowed.getIds());

    Query tenantIdQuery = null;
    if (securityConfiguration.getMultiTenancy().isChecksEnabled()) {
      tenantIdQuery = tenantId != null ? term(DecisionIndex.TENANT_ID, tenantId) : null;
    }
    return and(decisionIdQuery, tenantIdQuery);
  }
}
