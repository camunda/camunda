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
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.schema.indices.DecisionIndex.DECISION_REQUIREMENTS_KEY;
import static io.camunda.operate.schema.indices.DecisionRequirementsIndex.XML;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.dto.DecisionRequestDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
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

  private static final Logger logger = LoggerFactory.getLogger(DecisionReader.class);

  @Autowired private DecisionIndex decisionIndex;

  @Autowired private DecisionRequirementsIndex decisionRequirementsIndex;

  @Autowired(required = false)
  private PermissionsService permissionsService;

  @Autowired private OperateProperties operateProperties;

  private DecisionDefinitionEntity fromSearchHit(String processString) {
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
  public String getDiagram(Long decisionDefinitionKey) {
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
      final String message =
          String.format(
              "Exception occurred, while obtaining the decision diagram: %s", e.getMessage());
      logger.error(message, e);
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
  public DecisionDefinitionEntity getDecision(Long decisionDefinitionKey) {
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
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the decision: %s", e.getMessage());
      logger.error(message, e);
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
      DecisionRequestDto request) {
    final String tenantsGroupsAggName = "group_by_tenantId";
    final String groupsAggName = "group_by_decisionId";
    final String decisionsAggName = "decisions";

    AggregationBuilder agg =
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

    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().aggregation(agg).size(0);

    sourceBuilder.query(buildQuery(request.getTenantId()));

    final SearchRequest searchRequest =
        new SearchRequest(decisionIndex.getAlias()).source(sourceBuilder);

    try {
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      final Terms groups = searchResponse.getAggregations().get(tenantsGroupsAggName);
      Map<String, List<DecisionDefinitionEntity>> result = new HashMap<>();

      groups.getBuckets().stream()
          .forEach(
              b -> {
                final String groupTenantId = b.getKeyAsString();
                final Terms decisionGroups = b.getAggregations().get(groupsAggName);

                decisionGroups.getBuckets().stream()
                    .forEach(
                        tenantB -> {
                          final String decisionId = tenantB.getKeyAsString();
                          String groupKey = groupTenantId + "_" + decisionId;
                          result.put(groupKey, new ArrayList<>());

                          final TopHits processes = tenantB.getAggregations().get(decisionsAggName);
                          final SearchHit[] hits = processes.getHits().getHits();
                          for (SearchHit searchHit : hits) {
                            final DecisionDefinitionEntity decisionEntity =
                                fromSearchHit(searchHit.getSourceAsString());
                            result.get(groupKey).add(decisionEntity);
                          }
                        });
              });

      return result;
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining grouped processes: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private QueryBuilder buildQuery(String tenantId) {
    QueryBuilder decisionIdQ = null;
    if (permissionsService != null) {
      var allowed = permissionsService.getDecisionsWithPermission(IdentityPermission.READ);
      if (allowed != null && !allowed.isAll()) {
        decisionIdQ = QueryBuilders.termsQuery(DecisionIndex.DECISION_ID, allowed.getIds());
      }
    }
    QueryBuilder tenantIdQ = null;
    if (operateProperties.getMultiTenancy().isEnabled()) {
      tenantIdQ = tenantId != null ? termQuery(DecisionIndex.TENANT_ID, tenantId) : null;
    }
    QueryBuilder q = joinWithAnd(decisionIdQ, tenantIdQ);
    if (q == null) {
      q = matchAllQuery();
    }
    return q;
  }
}
