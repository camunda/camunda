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

import static io.camunda.operate.entities.FlowNodeState.ACTIVE;
import static io.camunda.operate.entities.FlowNodeState.COMPLETED;
import static io.camunda.operate.entities.FlowNodeState.TERMINATED;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.FLOW_NODE_ID;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.INCIDENT;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.STATE;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.TYPE;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.FlowNodeStatisticsDao;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeStatistics;
import io.camunda.operate.webapp.opensearch.OpensearchAggregationDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchFlowNodeStatisticsDao implements FlowNodeStatisticsDao {
  private static final int TERMS_AGG_SIZE = 10000;
  private final FlowNodeInstanceTemplate flowNodeInstanceTemplate;
  private final RichOpenSearchClient richOpenSearchClient;
  private final OpensearchQueryDSLWrapper queryDSLWrapper;
  private final OpensearchRequestDSLWrapper requestDSLWrapper;
  private final OpensearchAggregationDSLWrapper aggregationDSLWrapper;

  public OpensearchFlowNodeStatisticsDao(
      OpensearchQueryDSLWrapper queryDSLWrapper,
      OpensearchRequestDSLWrapper requestDSLWrapper,
      OpensearchAggregationDSLWrapper aggregationDSLWrapper,
      RichOpenSearchClient richOpenSearchClient,
      FlowNodeInstanceTemplate flowNodeInstanceTemplate) {
    this.flowNodeInstanceTemplate = flowNodeInstanceTemplate;
    this.richOpenSearchClient = richOpenSearchClient;
    this.queryDSLWrapper = queryDSLWrapper;
    this.requestDSLWrapper = requestDSLWrapper;
    this.aggregationDSLWrapper = aggregationDSLWrapper;
  }

  @Override
  public List<FlowNodeStatistics> getFlowNodeStatisticsForProcessInstance(Long processInstanceKey) {
    var requestBuilder =
        requestDSLWrapper
            .searchRequestBuilder(flowNodeInstanceTemplate)
            .query(
                queryDSLWrapper.withTenantCheck(
                    queryDSLWrapper.constantScore(
                        queryDSLWrapper.term(
                            FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceKey))))
            .aggregations(
                FLOW_NODE_ID_AGG,
                aggregationDSLWrapper.withSubaggregations(
                    aggregationDSLWrapper.termAggregation(FLOW_NODE_ID, TERMS_AGG_SIZE),
                    Map.of(
                        COUNT_INCIDENT, queryDSLWrapper.term(INCIDENT, true)._toAggregation(),
                        COUNT_CANCELED,
                            queryDSLWrapper
                                .and(
                                    queryDSLWrapper.not(
                                        queryDSLWrapper.term(
                                            TYPE, FlowNodeType.MULTI_INSTANCE_BODY.name())),
                                    queryDSLWrapper.term(STATE, TERMINATED.name()))
                                ._toAggregation(),
                        COUNT_COMPLETED,
                            queryDSLWrapper
                                .and(
                                    queryDSLWrapper.not(
                                        queryDSLWrapper.term(
                                            TYPE, FlowNodeType.MULTI_INSTANCE_BODY.name())),
                                    queryDSLWrapper.term(STATE, COMPLETED.name()))
                                ._toAggregation(),
                        COUNT_ACTIVE,
                            queryDSLWrapper
                                .and(
                                    queryDSLWrapper.not(
                                        queryDSLWrapper.term(
                                            TYPE, FlowNodeType.MULTI_INSTANCE_BODY.name())),
                                    queryDSLWrapper.term(STATE, ACTIVE.name()),
                                    queryDSLWrapper.term(INCIDENT, false))
                                ._toAggregation())))
            .size(0);

    return richOpenSearchClient
        .doc()
        .search(requestBuilder, Void.class)
        .aggregations()
        .get(FLOW_NODE_ID_AGG)
        .sterms()
        .buckets()
        .array()
        .stream()
        .map(
            bucket ->
                new FlowNodeStatistics()
                    .setActivityId(bucket.key())
                    .setCanceled(bucket.aggregations().get(COUNT_CANCELED).filter().docCount())
                    .setIncidents(bucket.aggregations().get(COUNT_INCIDENT).filter().docCount())
                    .setCompleted(bucket.aggregations().get(COUNT_COMPLETED).filter().docCount())
                    .setActive(bucket.aggregations().get(COUNT_ACTIVE).filter().docCount()))
        .toList();
  }
}
