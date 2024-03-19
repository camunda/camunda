/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations.TERMS_AGG_SIZE;
import static io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations.TOPHITS_AGG_SIZE;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.*;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

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

  @Autowired(required = false)
  private PermissionsService permissionsService;

  @Autowired private OperateProperties operateProperties;

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Override
  public String getDiagram(Long decisionDefinitionKey) {
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
  public DecisionDefinitionEntity getDecision(Long decisionDefinitionKey) {
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
      DecisionRequestDto request) {
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

  private Query buildQuery(String tenantId) {
    Query decisionIdQuery = null;
    if (permissionsService != null) {
      final var allowed = permissionsService.getDecisionsWithPermission(IdentityPermission.READ);
      if (allowed != null && !allowed.isAll()) {
        decisionIdQuery = stringTerms(DecisionIndex.DECISION_ID, allowed.getIds());
      }
    }
    Query tenantIdQuery = null;
    if (operateProperties.getMultiTenancy().isEnabled()) {
      tenantIdQuery = tenantId != null ? term(DecisionIndex.TENANT_ID, tenantId) : null;
    }
    final var query = and(decisionIdQuery, tenantIdQuery);
    return query == null ? matchAll() : query;
  }
}
