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
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.DecisionRequirementsDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
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
public class OpensearchDecisionRequirementsDao
    extends OpensearchKeyFilteringDao<DecisionRequirements, DecisionRequirements>
    implements DecisionRequirementsDao {

  private final DecisionRequirementsIndex decisionRequirementsIndex;

  public OpensearchDecisionRequirementsDao(
      OpensearchQueryDSLWrapper queryDSLWrapper,
      OpensearchRequestDSLWrapper requestDSLWrapper,
      RichOpenSearchClient richOpenSearchClient,
      DecisionRequirementsIndex decisionRequirementsIndex) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.decisionRequirementsIndex = decisionRequirementsIndex;
  }

  @Override
  protected String getKeyFieldName() {
    return DecisionRequirements.KEY;
  }

  @Override
  protected String getByKeyServerReadErrorMessage(Long key) {
    return String.format("Error in reading decision requirements for key %s", key);
  }

  @Override
  protected String getByKeyNoResultsErrorMessage(Long key) {
    return String.format("No decision requirements found for key %s", key);
  }

  @Override
  protected String getByKeyTooManyResultsErrorMessage(Long key) {
    return String.format("Found more than one decision requirements for key %s", key);
  }

  @Override
  public List<DecisionRequirements> byKeys(Set<Long> keys) throws APIException {
    final List<Long> nonNullKeys =
        (keys == null) ? List.of() : keys.stream().filter(Objects::nonNull).toList();
    if (nonNullKeys.isEmpty()) {
      return List.of();
    }
    try {
      var request =
          requestDSLWrapper
              .searchRequestBuilder(getIndexName())
              .query(queryDSLWrapper.longTerms(getKeyFieldName(), nonNullKeys));
      return richOpenSearchClient.doc().scrollValues(request, DecisionRequirements.class);
    } catch (Exception e) {
      throw new ServerException("Error in reading decision requirements by keys", e);
    }
  }

  @Override
  public String xmlByKey(Long key) throws APIException {
    validateKey(key);
    var request =
        requestDSLWrapper
            .searchRequestBuilder(getIndexName())
            .query(
                queryDSLWrapper.withTenantCheck(
                    queryDSLWrapper.term(DecisionRequirements.KEY, key)))
            .source(queryDSLWrapper.sourceInclude(DecisionRequirementsIndex.XML));
    try {
      var response = richOpenSearchClient.doc().search(request, Map.class);
      if (response.hits().total().value() == 1) {
        return response.hits().hits().get(0).source().get(DecisionRequirementsIndex.XML).toString();
      }
    } catch (Exception e) {
      throw new ServerException(
          String.format("Error in reading decision requirements as xml for key %s", key), e);
    }
    throw new ResourceNotFoundException(
        String.format("Decision requirements for key %s not found.", key));
  }

  @Override
  protected String getUniqueSortKey() {
    return DecisionRequirements.KEY;
  }

  @Override
  protected Class<DecisionRequirements> getInternalDocumentModelClass() {
    return DecisionRequirements.class;
  }

  @Override
  protected String getIndexName() {
    return decisionRequirementsIndex.getAlias();
  }

  @Override
  protected void buildFiltering(Query<DecisionRequirements> query, SearchRequest.Builder request) {
    final DecisionRequirements filter = query.getFilter();
    if (filter != null) {
      var queryTerms =
          Stream.of(
                  queryDSLWrapper.term(DecisionRequirements.ID, filter.getId()),
                  queryDSLWrapper.term(DecisionRequirements.KEY, filter.getKey()),
                  queryDSLWrapper.term(
                      DecisionRequirements.DECISION_REQUIREMENTS_ID,
                      filter.getDecisionRequirementsId()),
                  queryDSLWrapper.term(DecisionRequirements.TENANT_ID, filter.getTenantId()),
                  queryDSLWrapper.term(DecisionRequirements.NAME, filter.getName()),
                  queryDSLWrapper.term(DecisionRequirements.VERSION, filter.getVersion()),
                  queryDSLWrapper.term(
                      DecisionRequirements.RESOURCE_NAME, filter.getResourceName()))
              .filter(Objects::nonNull)
              .collect(Collectors.toList());

      if (!queryTerms.isEmpty()) {
        request.query(queryDSLWrapper.and(queryTerms));
      }
    }
  }

  @Override
  protected DecisionRequirements convertInternalToApiResult(DecisionRequirements internalResult) {
    return internalResult;
  }
}
