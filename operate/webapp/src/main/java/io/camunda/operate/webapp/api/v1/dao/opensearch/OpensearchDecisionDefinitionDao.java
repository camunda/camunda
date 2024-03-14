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
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.DecisionDefinitionDao;
import io.camunda.operate.webapp.api.v1.dao.DecisionRequirementsDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionDefinition;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchDecisionDefinitionDao
    extends OpensearchKeyFilteringDao<DecisionDefinition, DecisionDefinition>
    implements DecisionDefinitionDao {

  private final DecisionIndex decisionIndex;

  private final DecisionRequirementsIndex decisionRequirementsIndex;

  private final DecisionRequirementsDao decisionRequirementsDao;

  public OpensearchDecisionDefinitionDao(
      OpensearchQueryDSLWrapper queryDSLWrapper,
      OpensearchRequestDSLWrapper requestDSLWrapper,
      RichOpenSearchClient richOpenSearchClient,
      DecisionIndex decisionIndex,
      DecisionRequirementsIndex decisionRequirementsIndex,
      DecisionRequirementsDao decisionRequirementsDao) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.decisionIndex = decisionIndex;
    this.decisionRequirementsIndex = decisionRequirementsIndex;
    this.decisionRequirementsDao = decisionRequirementsDao;
  }

  @Override
  public DecisionDefinition byKey(Long key) {
    var decisionDefinition = super.byKey(key);
    DecisionRequirements decisionRequirements =
        decisionRequirementsDao.byKey(decisionDefinition.getDecisionRequirementsKey());
    decisionDefinition.setDecisionRequirementsName(decisionRequirements.getName());
    decisionDefinition.setDecisionRequirementsVersion(decisionRequirements.getVersion());
    return decisionDefinition;
  }

  @Override
  protected String getKeyFieldName() {
    return DecisionIndex.KEY;
  }

  @Override
  protected String getByKeyServerReadErrorMessage(Long key) {
    return String.format("Error in reading decision definition for key %s", key);
  }

  @Override
  protected String getByKeyNoResultsErrorMessage(Long key) {
    return String.format("No decision definition found for key %s", key);
  }

  @Override
  protected String getByKeyTooManyResultsErrorMessage(Long key) {
    return String.format("Found more than one decision definition for key %s", key);
  }

  @Override
  public Results<DecisionDefinition> search(Query<DecisionDefinition> query) {
    var results = super.search(query);
    var decisionDefinitions = results.getItems();
    populateDecisionRequirementsNameAndVersion(decisionDefinitions);
    return results;
  }

  @Override
  protected String getUniqueSortKey() {
    return DecisionDefinition.KEY;
  }

  @Override
  protected Class<DecisionDefinition> getInternalDocumentModelClass() {
    return DecisionDefinition.class;
  }

  @Override
  protected String getIndexName() {
    return decisionIndex.getAlias();
  }

  @Override
  protected void buildFiltering(Query<DecisionDefinition> query, SearchRequest.Builder request) {
    DecisionDefinition filter = query.getFilter();

    if (filter != null) {
      var queryTerms =
          Stream.of(
                  queryDSLWrapper.term(DecisionDefinition.ID, filter.getId()),
                  queryDSLWrapper.term(DecisionDefinition.KEY, filter.getKey()),
                  queryDSLWrapper.term(DecisionDefinition.DECISION_ID, filter.getDecisionId()),
                  queryDSLWrapper.term(DecisionDefinition.TENANT_ID, filter.getTenantId()),
                  queryDSLWrapper.term(DecisionDefinition.NAME, filter.getName()),
                  queryDSLWrapper.term(DecisionDefinition.VERSION, filter.getVersion()),
                  queryDSLWrapper.term(
                      DecisionDefinition.DECISION_REQUIREMENTS_ID,
                      filter.getDecisionRequirementsId()),
                  queryDSLWrapper.term(
                      DecisionDefinition.DECISION_REQUIREMENTS_KEY,
                      filter.getDecisionRequirementsKey()),
                  buildFilteringBy(
                      filter.getDecisionRequirementsName(),
                      filter.getDecisionRequirementsVersion()))
              .filter(Objects::nonNull)
              .collect(Collectors.toList());

      if (!queryTerms.isEmpty()) {
        request.query(queryDSLWrapper.and(queryTerms));
      }
    }
  }

  @Override
  protected DecisionDefinition convertInternalToApiResult(DecisionDefinition internalResult) {
    return internalResult;
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

  private org.opensearch.client.opensearch._types.query_dsl.Query buildFilteringBy(
      String decisionRequirementsName, Integer decisionRequirementsVersion) {
    try {
      List<org.opensearch.client.opensearch._types.query_dsl.Query> queryTerms = new LinkedList<>();
      queryTerms.add(
          queryDSLWrapper.term(DecisionRequirementsIndex.NAME, decisionRequirementsName));
      queryTerms.add(
          queryDSLWrapper.term(DecisionRequirementsIndex.VERSION, decisionRequirementsVersion));
      var query = queryDSLWrapper.and(queryTerms);
      if (query == null) {
        return null;
      }
      var request =
          requestDSLWrapper
              .searchRequestBuilder(decisionRequirementsIndex.getAlias())
              .query(queryDSLWrapper.withTenantCheck(query))
              .source(queryDSLWrapper.sourceInclude(DecisionRequirementsIndex.KEY));
      var decisionRequirements =
          richOpenSearchClient.doc().scrollValues(request, DecisionRequirements.class);
      final List<Long> nonNullKeys =
          decisionRequirements.stream()
              .map(DecisionRequirements::getKey)
              .filter(Objects::nonNull)
              .toList();
      if (nonNullKeys.isEmpty()) {
        return queryDSLWrapper.matchNone();
      }
      return queryDSLWrapper.longTerms(DecisionDefinition.DECISION_REQUIREMENTS_KEY, nonNullKeys);
    } catch (Exception e) {
      throw new ServerException("Error in reading decision requirements by name and version", e);
    }
  }
}
