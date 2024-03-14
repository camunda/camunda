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

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.FlowNodeInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchFlowNodeInstanceDao
    extends OpensearchKeyFilteringDao<FlowNodeInstance, FlowNodeInstance>
    implements FlowNodeInstanceDao {

  private final FlowNodeInstanceTemplate flowNodeInstanceIndex;
  private final ProcessCache processCache;

  private final OperateDateTimeFormatter dateTimeFormatter;

  public OpensearchFlowNodeInstanceDao(
      final OpensearchQueryDSLWrapper queryDSLWrapper,
      final OpensearchRequestDSLWrapper requestDSLWrapper,
      final RichOpenSearchClient richOpenSearchClient,
      final FlowNodeInstanceTemplate flowNodeInstanceIndex,
      final ProcessCache processCache,
      final OperateDateTimeFormatter dateTimeFormatter) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.flowNodeInstanceIndex = flowNodeInstanceIndex;
    this.processCache = processCache;
    this.dateTimeFormatter = dateTimeFormatter;
  }

  @Override
  protected String getKeyFieldName() {
    return FlowNodeInstance.KEY;
  }

  @Override
  protected String getByKeyServerReadErrorMessage(final Long key) {
    return String.format("Error in reading flownode instance for key %s", key);
  }

  @Override
  protected String getByKeyNoResultsErrorMessage(final Long key) {
    return String.format("No flownode instance found for key %s", key);
  }

  @Override
  protected String getByKeyTooManyResultsErrorMessage(final Long key) {
    return String.format("Found more than one flownode instances for key %s", key);
  }

  @Override
  protected String getUniqueSortKey() {
    return FlowNodeInstance.KEY;
  }

  @Override
  protected Class<FlowNodeInstance> getInternalDocumentModelClass() {
    return FlowNodeInstance.class;
  }

  @Override
  protected String getIndexName() {
    return flowNodeInstanceIndex.getAlias();
  }

  @Override
  protected void buildFiltering(
      final Query<FlowNodeInstance> query, final SearchRequest.Builder request) {
    final FlowNodeInstance filter = query.getFilter();

    if (filter != null) {
      final var queryTerms =
          Stream.of(
                  queryDSLWrapper.term(FlowNodeInstance.KEY, filter.getKey()),
                  queryDSLWrapper.term(
                      FlowNodeInstance.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()),
                  queryDSLWrapper.term(
                      FlowNodeInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey()),
                  queryDSLWrapper.matchDateQuery(
                      FlowNodeInstance.START_DATE,
                      filter.getStartDate(),
                      dateTimeFormatter.getApiDateTimeFormatString()),
                  queryDSLWrapper.matchDateQuery(
                      FlowNodeInstance.END_DATE,
                      filter.getEndDate(),
                      dateTimeFormatter.getApiDateTimeFormatString()),
                  queryDSLWrapper.term(FlowNodeInstance.STATE, filter.getState()),
                  queryDSLWrapper.term(FlowNodeInstance.TYPE, filter.getType()),
                  queryDSLWrapper.term(FlowNodeInstance.FLOW_NODE_ID, filter.getFlowNodeId()),
                  queryDSLWrapper.term(FlowNodeInstance.INCIDENT, filter.getIncident()),
                  queryDSLWrapper.term(FlowNodeInstance.INCIDENT_KEY, filter.getIncidentKey()),
                  queryDSLWrapper.term(FlowNodeInstance.TENANT_ID, filter.getTenantId()))
              .filter(Objects::nonNull)
              .collect(Collectors.toList());

      if (!queryTerms.isEmpty()) {
        request.query(queryDSLWrapper.and(queryTerms));
      }
    }
  }

  @Override
  protected FlowNodeInstance convertInternalToApiResult(final FlowNodeInstance internalResult) {
    if (internalResult != null) {
      if (StringUtils.isNotEmpty(internalResult.getStartDate())) {
        internalResult.setStartDate(
            dateTimeFormatter.convertGeneralToApiDateTime(internalResult.getStartDate()));
      }
      if (StringUtils.isNotEmpty(internalResult.getEndDate())) {
        internalResult.setEndDate(
            dateTimeFormatter.convertGeneralToApiDateTime(internalResult.getEndDate()));
      }

      if (internalResult.getFlowNodeId() != null) {
        final String flowNodeName =
            processCache.getFlowNodeNameOrDefaultValue(
                internalResult.getProcessDefinitionKey(), internalResult.getFlowNodeId(), null);
        internalResult.setFlowNodeName(flowNodeName);
      }
    }
    return internalResult;
  }
}
