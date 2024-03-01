/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.dao.DecisionDefinitionDao;
import io.camunda.operate.webapp.api.v1.dao.DecisionRequirementsDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionDefinition;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component("ElasticsearchDecisionDefinitionDaoV1")
public class ElasticsearchDecisionDefinitionDao extends ElasticsearchDao<DecisionDefinition>
    implements DecisionDefinitionDao {

  @Autowired private DecisionIndex decisionIndex;

  @Autowired private DecisionRequirementsIndex decisionRequirementsIndex;

  @Autowired private DecisionRequirementsDao decisionRequirementsDao;

  @Override
  public DecisionDefinition byKey(Long key) throws APIException {
    List<DecisionDefinition> decisionDefinitions;
    try {
      decisionDefinitions =
          searchFor(new SearchSourceBuilder().query(termQuery(DecisionIndex.KEY, key)));
    } catch (Exception e) {
      throw new ServerException(
          String.format("Error in reading decision definition for key %s", key), e);
    }
    if (decisionDefinitions.isEmpty()) {
      throw new ResourceNotFoundException(
          String.format("No decision definition found for key %s", key));
    }
    if (decisionDefinitions.size() > 1) {
      throw new ServerException(
          String.format("Found more than one decision definition for key %s", key));
    }

    DecisionDefinition decisionDefinition = decisionDefinitions.get(0);
    DecisionRequirements decisionRequirements =
        decisionRequirementsDao.byKey(decisionDefinition.getDecisionRequirementsKey());
    decisionDefinition.setDecisionRequirementsName(decisionRequirements.getName());
    decisionDefinition.setDecisionRequirementsVersion(decisionRequirements.getVersion());

    return decisionDefinition;
  }

  @Override
  public Results<DecisionDefinition> search(Query<DecisionDefinition> query) throws APIException {

    final SearchSourceBuilder searchSourceBuilder =
        buildQueryOn(query, DecisionDefinition.KEY, new SearchSourceBuilder());
    try {
      final SearchRequest searchRequest =
          new SearchRequest().indices(decisionIndex.getAlias()).source(searchSourceBuilder);
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        final Object[] sortValues = searchHitArray[searchHitArray.length - 1].getSortValues();
        List<DecisionDefinition> decisionDefinitions =
            ElasticsearchUtil.mapSearchHits(searchHitArray, objectMapper, DecisionDefinition.class);
        populateDecisionRequirementsNameAndVersion(decisionDefinitions);
        return new Results<DecisionDefinition>()
            .setTotal(searchHits.getTotalHits().value)
            .setItems(decisionDefinitions)
            .setSortValues(sortValues);
      } else {
        return new Results<DecisionDefinition>().setTotal(searchHits.getTotalHits().value);
      }
    } catch (Exception e) {
      throw new ServerException("Error in reading decision definitions", e);
    }
  }

  protected List<DecisionDefinition> searchFor(final SearchSourceBuilder searchSource)
      throws IOException {
    final SearchRequest searchRequest =
        new SearchRequest(decisionIndex.getAlias()).source(searchSource);
    return tenantAwareClient.search(
        searchRequest,
        () -> {
          return ElasticsearchUtil.scroll(
              searchRequest, DecisionDefinition.class, objectMapper, elasticsearch);
        });
  }

  protected void buildFiltering(
      final Query<DecisionDefinition> query, final SearchSourceBuilder searchSourceBuilder) {
    final DecisionDefinition filter = query.getFilter();
    if (filter != null) {
      List<QueryBuilder> queryBuilders = new ArrayList<>();
      queryBuilders.add(buildTermQuery(DecisionDefinition.ID, filter.getId()));
      queryBuilders.add(buildTermQuery(DecisionDefinition.KEY, filter.getKey()));
      queryBuilders.add(buildTermQuery(DecisionDefinition.DECISION_ID, filter.getDecisionId()));
      queryBuilders.add(buildTermQuery(DecisionDefinition.TENANT_ID, filter.getTenantId()));
      queryBuilders.add(buildTermQuery(DecisionDefinition.NAME, filter.getName()));
      queryBuilders.add(buildTermQuery(DecisionDefinition.VERSION, filter.getVersion()));
      queryBuilders.add(
          buildTermQuery(
              DecisionDefinition.DECISION_REQUIREMENTS_ID, filter.getDecisionRequirementsId()));
      queryBuilders.add(
          buildTermQuery(
              DecisionDefinition.DECISION_REQUIREMENTS_KEY, filter.getDecisionRequirementsKey()));
      queryBuilders.add(
          buildFilteringBy(
              filter.getDecisionRequirementsName(), filter.getDecisionRequirementsVersion()));

      searchSourceBuilder.query(
          ElasticsearchUtil.joinWithAnd(queryBuilders.toArray(new QueryBuilder[] {})));
    }
  }

  /**
   * buildFilteringBy
   *
   * @return the query to filter decision definitions by decisionRequirementsName and
   *     decisionRequirementsVersion, or null if no filter is needed
   */
  private QueryBuilder buildFilteringBy(
      String decisionRequirementsName, Integer decisionRequirementsVersion) {

    List<QueryBuilder> queryBuilders = new ArrayList<>();
    queryBuilders.add(buildTermQuery(DecisionRequirementsIndex.NAME, decisionRequirementsName));
    queryBuilders.add(
        buildTermQuery(DecisionRequirementsIndex.VERSION, decisionRequirementsVersion));

    QueryBuilder query =
        ElasticsearchUtil.joinWithAnd(queryBuilders.toArray(new QueryBuilder[] {}));
    if (query == null) {
      return null;
    }

    SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(query).fetchSource(DecisionRequirementsIndex.KEY, null);
    SearchRequest searchRequest =
        new SearchRequest(decisionRequirementsIndex.getAlias()).source(searchSourceBuilder);
    try {
      List<DecisionRequirements> decisionRequirements =
          tenantAwareClient.search(
              searchRequest,
              () -> {
                return ElasticsearchUtil.scroll(
                    searchRequest, DecisionRequirements.class, objectMapper, elasticsearch);
              });
      final List<Long> nonNullKeys =
          decisionRequirements.stream()
              .map(DecisionRequirements::getKey)
              .filter(Objects::nonNull)
              .toList();
      if (nonNullKeys.isEmpty()) {
        return ElasticsearchUtil.createMatchNoneQuery();
      }
      return termsQuery(DecisionDefinition.DECISION_REQUIREMENTS_KEY, nonNullKeys);
    } catch (Exception e) {
      throw new ServerException("Error in reading decision requirements by name and version", e);
    }
  }

  /**
   * populateDecisionRequirementsNameAndVersion - adds decisionRequirementsName and
   * decisionRequirementsVersion fields to the decision definitions
   */
  private void populateDecisionRequirementsNameAndVersion(
      List<DecisionDefinition> decisionDefinitions) {
    Set<Long> decisionRequirementsKeys =
        decisionDefinitions.stream()
            .map(DecisionDefinition::getDecisionRequirementsKey)
            .collect(Collectors.toSet());
    List<DecisionRequirements> decisionRequirements =
        decisionRequirementsDao.byKeys(decisionRequirementsKeys);

    Map<Long, DecisionRequirements> decisionReqMap = new HashMap<>();
    decisionRequirements.forEach(
        decisionReq -> decisionReqMap.put(decisionReq.getKey(), decisionReq));
    decisionDefinitions.forEach(
        decisionDef -> {
          DecisionRequirements decisionReq =
              (decisionDef.getDecisionRequirementsKey() == null)
                  ? null
                  : decisionReqMap.get(decisionDef.getDecisionRequirementsKey());
          if (decisionReq != null) {
            decisionDef.setDecisionRequirementsName(decisionReq.getName());
            decisionDef.setDecisionRequirementsVersion(decisionReq.getVersion());
          }
        });
  }
}
