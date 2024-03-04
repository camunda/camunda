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
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.dao.DecisionRequirementsDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import java.io.IOException;
import java.util.*;
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
@Component("ElasticsearchDecisionRequirementsDaoV1")
public class ElasticsearchDecisionRequirementsDao extends ElasticsearchDao<DecisionRequirements>
    implements DecisionRequirementsDao {

  @Autowired private DecisionRequirementsIndex decisionRequirementsIndex;

  @Override
  public DecisionRequirements byKey(Long key) throws APIException {
    List<DecisionRequirements> decisionRequirements;
    try {
      decisionRequirements =
          searchFor(new SearchSourceBuilder().query(termQuery(DecisionRequirementsIndex.KEY, key)));
    } catch (Exception e) {
      throw new ServerException(
          String.format("Error in reading decision requirements for key %s", key), e);
    }
    if (decisionRequirements.isEmpty()) {
      throw new ResourceNotFoundException(
          String.format("No decision requirements found for key %s", key));
    }
    if (decisionRequirements.size() > 1) {
      throw new ServerException(
          String.format("Found more than one decision requirements for key %s", key));
    }
    return decisionRequirements.get(0);
  }

  @Override
  public List<DecisionRequirements> byKeys(Set<Long> keys) throws APIException {
    final List<Long> nonNullKeys =
        (keys == null) ? List.of() : keys.stream().filter(Objects::nonNull).toList();
    if (nonNullKeys.isEmpty()) {
      return List.of();
    }
    try {
      return searchFor(
          new SearchSourceBuilder().query(termsQuery(DecisionRequirementsIndex.KEY, nonNullKeys)));
    } catch (Exception e) {
      throw new ServerException("Error in reading decision requirements by keys", e);
    }
  }

  @Override
  public String xmlByKey(final Long key) throws APIException {
    try {
      final SearchRequest searchRequest =
          new SearchRequest(decisionRequirementsIndex.getAlias())
              .source(
                  new SearchSourceBuilder()
                      .query(termQuery(DecisionRequirementsIndex.KEY, key))
                      .fetchSource(DecisionRequirementsIndex.XML, null));
      final SearchResponse response = tenantAwareClient.search(searchRequest);
      if (response.getHits().getTotalHits().value == 1) {
        Map<String, Object> result = response.getHits().getHits()[0].getSourceAsMap();
        return (String) result.get(DecisionRequirementsIndex.XML);
      }
    } catch (IOException e) {
      throw new ServerException(
          String.format("Error in reading decision requirements as xml for key %s", key), e);
    }
    throw new ResourceNotFoundException(
        String.format("Decision requirements for key %s not found.", key));
  }

  @Override
  public Results<DecisionRequirements> search(Query<DecisionRequirements> query)
      throws APIException {

    final SearchSourceBuilder searchSourceBuilder =
        buildQueryOn(query, DecisionRequirements.KEY, new SearchSourceBuilder());
    try {
      final SearchRequest searchRequest =
          new SearchRequest()
              .indices(decisionRequirementsIndex.getAlias())
              .source(searchSourceBuilder);
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        final Object[] sortValues = searchHitArray[searchHitArray.length - 1].getSortValues();
        List<DecisionRequirements> decisionRequirements =
            ElasticsearchUtil.mapSearchHits(
                searchHitArray, objectMapper, DecisionRequirements.class);
        return new Results<DecisionRequirements>()
            .setTotal(searchHits.getTotalHits().value)
            .setItems(decisionRequirements)
            .setSortValues(sortValues);
      } else {
        return new Results<DecisionRequirements>().setTotal(searchHits.getTotalHits().value);
      }
    } catch (Exception e) {
      throw new ServerException("Error in reading decision requirements", e);
    }
  }

  protected List<DecisionRequirements> searchFor(final SearchSourceBuilder searchSource)
      throws IOException {
    final SearchRequest searchRequest =
        new SearchRequest(decisionRequirementsIndex.getAlias()).source(searchSource);
    return tenantAwareClient.search(
        searchRequest,
        () -> {
          return ElasticsearchUtil.scroll(
              searchRequest, DecisionRequirements.class, objectMapper, elasticsearch);
        });
  }

  @Override
  protected void buildFiltering(
      Query<DecisionRequirements> query, SearchSourceBuilder searchSourceBuilder) {
    final DecisionRequirements filter = query.getFilter();
    if (filter != null) {
      List<QueryBuilder> queryBuilders = new ArrayList<>();
      queryBuilders.add(buildTermQuery(DecisionRequirements.ID, filter.getId()));
      queryBuilders.add(buildTermQuery(DecisionRequirements.KEY, filter.getKey()));
      queryBuilders.add(
          buildTermQuery(
              DecisionRequirements.DECISION_REQUIREMENTS_ID, filter.getDecisionRequirementsId()));
      queryBuilders.add(buildTermQuery(DecisionRequirements.TENANT_ID, filter.getTenantId()));
      queryBuilders.add(buildTermQuery(DecisionRequirements.NAME, filter.getName()));
      queryBuilders.add(buildTermQuery(DecisionRequirements.VERSION, filter.getVersion()));
      queryBuilders.add(
          buildTermQuery(DecisionRequirements.RESOURCE_NAME, filter.getResourceName()));

      searchSourceBuilder.query(
          ElasticsearchUtil.joinWithAnd(queryBuilders.toArray(new QueryBuilder[] {})));
    }
  }
}
