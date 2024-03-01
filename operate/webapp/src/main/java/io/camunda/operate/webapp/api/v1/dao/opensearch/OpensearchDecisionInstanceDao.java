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
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.DecisionInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchDecisionInstanceDao
    extends OpensearchSearchableDao<DecisionInstance, DecisionInstance>
    implements DecisionInstanceDao {

  private final DecisionInstanceTemplate decisionInstanceTemplate;

  private final OperateDateTimeFormatter dateTimeFormatter;

  public OpensearchDecisionInstanceDao(
      final OpensearchQueryDSLWrapper queryDSLWrapper,
      final OpensearchRequestDSLWrapper requestDSLWrapper,
      final RichOpenSearchClient richOpenSearchClient,
      final DecisionInstanceTemplate decisionInstanceTemplate,
      final OperateDateTimeFormatter dateTimeFormatter) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.decisionInstanceTemplate = decisionInstanceTemplate;
    this.dateTimeFormatter = dateTimeFormatter;
  }

  @Override
  public DecisionInstance byId(final String id) throws APIException {
    if (id == null) {
      throw new ServerException("ID provided cannot be null");
    }

    final List<DecisionInstance> decisionInstances;
    try {
      final var request =
          requestDSLWrapper
              .searchRequestBuilder(getIndexName())
              .query(
                  queryDSLWrapper.withTenantCheck(
                      queryDSLWrapper.term(DecisionInstanceTemplate.ID, id)));
      decisionInstances =
          richOpenSearchClient.doc().searchValues(request, getInternalDocumentModelClass());
    } catch (final Exception e) {
      throw new ServerException(
          String.format("Error in reading decision instance for id %s", id), e);
    }
    if (decisionInstances.isEmpty()) {
      throw new ResourceNotFoundException(
          String.format("No decision instance found for id %s", id));
    }
    if (decisionInstances.size() > 1) {
      throw new ServerException(
          String.format("Found more than one decision instance for id %s", id));
    }
    return convertInternalToApiResult(decisionInstances.get(0));
  }

  @Override
  protected SearchRequest.Builder buildSearchRequest(final Query<DecisionInstance> query) {
    return super.buildSearchRequest(query)
        .source(
            queryDSLWrapper.sourceExclude(
                DecisionInstanceTemplate.EVALUATED_INPUTS,
                DecisionInstanceTemplate.EVALUATED_OUTPUTS));
  }

  @Override
  protected String getUniqueSortKey() {
    return DecisionInstance.ID;
  }

  @Override
  protected Class<DecisionInstance> getInternalDocumentModelClass() {
    return DecisionInstance.class;
  }

  @Override
  protected String getIndexName() {
    return decisionInstanceTemplate.getAlias();
  }

  @Override
  protected void buildFiltering(
      final Query<DecisionInstance> query, final SearchRequest.Builder request) {
    final DecisionInstance filter = query.getFilter();
    if (filter != null) {
      final var queryTerms =
          Stream.of(
                  queryDSLWrapper.term(DecisionInstance.ID, filter.getId()),
                  queryDSLWrapper.term(DecisionInstance.KEY, filter.getKey()),
                  queryDSLWrapper.term(
                      DecisionInstance.STATE,
                      filter.getState() == null ? null : filter.getState().name()),
                  queryDSLWrapper.matchDateQuery(
                      DecisionInstance.EVALUATION_DATE,
                      filter.getEvaluationDate(),
                      dateTimeFormatter.getApiDateTimeFormatString()),
                  queryDSLWrapper.term(
                      DecisionInstance.EVALUATION_FAILURE, filter.getEvaluationFailure()),
                  queryDSLWrapper.term(
                      DecisionInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey()),
                  queryDSLWrapper.term(
                      DecisionInstance.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()),
                  queryDSLWrapper.term(DecisionInstance.DECISION_ID, filter.getDecisionId()),
                  queryDSLWrapper.term(DecisionInstance.TENANT_ID, filter.getTenantId()),
                  queryDSLWrapper.term(
                      DecisionInstance.DECISION_DEFINITION_ID, filter.getDecisionDefinitionId()),
                  queryDSLWrapper.term(DecisionInstance.DECISION_NAME, filter.getDecisionName()),
                  queryDSLWrapper.term(
                      DecisionInstance.DECISION_VERSION, filter.getDecisionVersion()),
                  queryDSLWrapper.term(
                      DecisionInstance.DECISION_TYPE,
                      filter.getDecisionType() == null ? null : filter.getDecisionType().name()))
              .filter(Objects::nonNull)
              .collect(Collectors.toList());

      if (!queryTerms.isEmpty()) {
        request.query(queryDSLWrapper.and(queryTerms));
      }
    }
  }

  @Override
  protected DecisionInstance convertInternalToApiResult(final DecisionInstance internalResult) {
    if (internalResult != null && StringUtils.isNotEmpty(internalResult.getEvaluationDate())) {
      internalResult.setEvaluationDate(
          dateTimeFormatter.convertGeneralToApiDateTime(internalResult.getEvaluationDate()));
    }

    return internalResult;
  }
}
