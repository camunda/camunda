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

import static io.camunda.operate.entities.FlowNodeState.ACTIVE;
import static io.camunda.operate.entities.FlowNodeState.COMPLETED;
import static io.camunda.operate.entities.FlowNodeState.TERMINATED;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.FLOW_NODE_ID;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.INCIDENT;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.STATE;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.TYPE;
import static io.camunda.operate.util.ElasticsearchUtil.TERMS_AGG_SIZE;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.dao.FlowNodeStatisticsDao;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeStatistics;
import io.camunda.operate.webapp.api.v1.entities.Query;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component("ElasticsearchFlowNodeStatisticsDaoV1")
public class ElasticsearchFlowNodeStatisticsDao extends ElasticsearchDao<FlowNodeStatistics>
    implements FlowNodeStatisticsDao {
  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Override
  protected void buildFiltering(
      Query<FlowNodeStatistics> query, SearchSourceBuilder searchSourceBuilder) {

    final FlowNodeStatistics filter = query.getFilter();
    final List<QueryBuilder> queryBuilders = new ArrayList<>();
    if (filter != null) {
      queryBuilders.add(buildTermQuery(FlowNodeStatistics.ACTIVITY_ID, filter.getActivityId()));
    }
    searchSourceBuilder.query(joinWithAnd(queryBuilders.toArray(new QueryBuilder[] {})));
  }

  @Override
  public List<FlowNodeStatistics> getFlowNodeStatisticsForProcessInstance(Long processInstanceKey) {
    try {
      final SearchRequest request =
          ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate)
              .source(
                  new SearchSourceBuilder()
                      .query(
                          constantScoreQuery(
                              termQuery(
                                  FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY,
                                  processInstanceKey)))
                      .aggregation(
                          terms(FLOW_NODE_ID_AGG)
                              .field(FLOW_NODE_ID)
                              .size(TERMS_AGG_SIZE)
                              .subAggregation(
                                  filter(
                                      COUNT_INCIDENT,
                                      boolQuery()
                                          // Need to count when MULTI_INSTANCE_BODY itself has an
                                          // incident
                                          // .mustNot(termQuery(TYPE,
                                          // FlowNodeType.MULTI_INSTANCE_BODY))
                                          .must(termQuery(INCIDENT, true))))
                              .subAggregation(
                                  filter(
                                      COUNT_CANCELED,
                                      boolQuery()
                                          .mustNot(
                                              termQuery(TYPE, FlowNodeType.MULTI_INSTANCE_BODY))
                                          .must(termQuery(STATE, TERMINATED))))
                              .subAggregation(
                                  filter(
                                      COUNT_COMPLETED,
                                      boolQuery()
                                          .mustNot(
                                              termQuery(TYPE, FlowNodeType.MULTI_INSTANCE_BODY))
                                          .must(termQuery(STATE, COMPLETED))))
                              .subAggregation(
                                  filter(
                                      COUNT_ACTIVE,
                                      boolQuery()
                                          .mustNot(
                                              termQuery(TYPE, FlowNodeType.MULTI_INSTANCE_BODY))
                                          .must(termQuery(STATE, ACTIVE))
                                          .must(termQuery(INCIDENT, false)))))
                      .size(0));
      final SearchResponse response = tenantAwareClient.search(request);
      final Aggregations aggregations = response.getAggregations();
      final Terms flowNodeAgg = aggregations.get(FLOW_NODE_ID_AGG);
      return flowNodeAgg.getBuckets().stream()
          .map(
              bucket ->
                  new FlowNodeStatistics()
                      .setActivityId(bucket.getKeyAsString())
                      .setCanceled(
                          ((Filter) bucket.getAggregations().get(COUNT_CANCELED)).getDocCount())
                      .setIncidents(
                          ((Filter) bucket.getAggregations().get(COUNT_INCIDENT)).getDocCount())
                      .setCompleted(
                          ((Filter) bucket.getAggregations().get(COUNT_COMPLETED)).getDocCount())
                      .setActive(
                          ((Filter) bucket.getAggregations().get(COUNT_ACTIVE)).getDocCount()))
          .collect(Collectors.toList());
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining statistics for process instance flow nodes: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }
}
