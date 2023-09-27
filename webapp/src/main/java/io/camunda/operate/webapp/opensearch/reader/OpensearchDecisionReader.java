/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.opensearch.reader;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.reader.DecisionReader;
import io.camunda.operate.webapp.rest.dto.DecisionRequestDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations.TERMS_AGG_SIZE;
import static io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations.TOPHITS_AGG_SIZE;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.*;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchDecisionReader implements DecisionReader {

  private static final Logger logger = LoggerFactory.getLogger(OpensearchDecisionReader.class);

  @Autowired
  private DecisionIndex decisionIndex;

  @Autowired
  private DecisionRequirementsIndex decisionRequirementsIndex;

  @Autowired(required = false)
  private PermissionsService permissionsService;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

  @Override
  public String getDiagram(Long decisionDefinitionKey) {
    record decisionRequirementsIdRecord(Long decisionRequirementsKey){}
    var request = searchRequestBuilder(decisionIndex.getAlias())
        .query(withTenantCheck(ids(decisionDefinitionKey.toString())));
    var hits = richOpenSearchClient.doc().search(request, decisionRequirementsIdRecord.class).hits();
    if (hits.total().value() == 0){
      throw new NotFoundException("No decision definition found for id " + decisionDefinitionKey);
    }
    var decisionRequirementsId = hits.hits().get(0).source().decisionRequirementsKey;

    var xmlRequest = searchRequestBuilder(decisionRequirementsIndex.getAlias())
        .query(withTenantCheck(ids(decisionRequirementsId.toString())))
        .source(sourceInclude(DecisionRequirementsIndex.XML));
    record xmlRecord(String xml){}
    var xmlHits = richOpenSearchClient.doc().search(xmlRequest, xmlRecord.class).hits();
    if (xmlHits.total().value() == 1){
      return xmlHits.hits().get(0).source().xml;
    } else if (hits.total().value() > 1) {
      throw new NotFoundException(String.format("Could not find unique DRD with id '%s'.", decisionRequirementsId));
    } else {
      throw new NotFoundException(String.format("Could not find DRD with id '%s'.", decisionRequirementsId));
    }
  }

  @Override
  public DecisionDefinitionEntity getDecision(Long decisionDefinitionKey) {
    var request = searchRequestBuilder(decisionIndex.getAlias())
        .query(withTenantCheck(
            term(DecisionIndex.KEY, decisionDefinitionKey)));
    var hits = richOpenSearchClient.doc().search(request, DecisionDefinitionEntity.class).hits();
    if( hits.total().value() == 1) {
      return hits.hits().get(0).source();
    } else if (hits.total().value() > 1) {
      throw new NotFoundException(String.format("Could not find unique decision with key '%s'.", decisionDefinitionKey));
    } else {
      throw new NotFoundException(String.format("Could not find decision with key '%s'.", decisionDefinitionKey));
    }
  }

  @Override
  public Map<String, List<DecisionDefinitionEntity>> getDecisionsGrouped(DecisionRequestDto request) {
    var tenantsGroupsAggName = "group_by_tenantId";
    var groupsAggName = "group_by_decisionId";
    var decisionsAggName = "decisions";
    var  sourceFields = List.of(DecisionIndex.ID, DecisionIndex.NAME, DecisionIndex.VERSION,
        DecisionIndex.DECISION_ID, DecisionIndex.TENANT_ID);

    var aggregationsRequest = searchRequestBuilder(decisionIndex.getAlias())
        .query(withTenantCheck(buildQuery(request.getTenantId())))
        .size(0)
        .aggregations(tenantsGroupsAggName, withSubaggregations(
            termAggregation(ProcessIndex.TENANT_ID, TERMS_AGG_SIZE),
            Map.of(groupsAggName, withSubaggregations(
                termAggregation(DecisionIndex.DECISION_ID, TERMS_AGG_SIZE),
                Map.of(decisionsAggName, topHitsAggregation(sourceFields, TOPHITS_AGG_SIZE, sortOptions(DecisionIndex.VERSION, SortOrder.Desc))._toAggregation())
            ))
        ));

    Map<String, List<DecisionDefinitionEntity>> result = new HashMap<>();
    var response = richOpenSearchClient.doc().search(aggregationsRequest, Object.class);

    response.aggregations().get(tenantsGroupsAggName).sterms().buckets().array().forEach( tenantBucket ->
        tenantBucket.aggregations().get(groupsAggName).sterms().buckets().array().forEach(decisionIdBucket -> {
          String key = tenantBucket.key() + "_" + decisionIdBucket.key();
          List<DecisionDefinitionEntity> value = decisionIdBucket.aggregations().get(decisionsAggName)
              .topHits().hits().hits().stream()
              .map(h -> h.source().to(DecisionDefinitionEntity.class))
              .toList();
          result.put(key,value);
        } )
    );

    return result;
  }

  private Query buildQuery(String tenantId){
    Query decisionIdQuery = null;
    if (permissionsService != null) {
      var allowed = permissionsService.getDecisionsWithPermission(IdentityPermission.READ);
      if (allowed != null && !allowed.isAll()) {
        decisionIdQuery = stringTerms(DecisionIndex.DECISION_ID, allowed.getIds());
      }
    }
    Query tenantIdQuery = null;
    if (operateProperties.getMultiTenancy().isEnabled()) {
      tenantIdQuery = tenantId != null ? term(DecisionIndex.TENANT_ID, tenantId ) : null;
    }
    var query = and(decisionIdQuery, tenantIdQuery);
    return query==null? matchAll(): query;
  }
}
