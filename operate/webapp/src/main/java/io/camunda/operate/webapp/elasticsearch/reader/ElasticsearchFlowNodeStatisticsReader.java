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

import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITY_ID;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITY_STATE;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITY_TYPE;
import static io.camunda.operate.schema.templates.ListViewTemplate.INCIDENT;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.join.aggregations.JoinAggregationBuilders.children;
import static org.elasticsearch.join.aggregations.JoinAggregationBuilders.parent;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.elasticsearch.QueryHelper;
import io.camunda.operate.webapp.reader.FlowNodeStatisticsReader;
import io.camunda.operate.webapp.rest.dto.FlowNodeStatisticsDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.join.aggregations.Children;
import org.elasticsearch.join.aggregations.ChildrenAggregationBuilder;
import org.elasticsearch.join.aggregations.Parent;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchFlowNodeStatisticsReader implements FlowNodeStatisticsReader {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchFlowNodeStatisticsReader.class);

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private QueryHelper queryHelper;

  @Override
  public Collection<FlowNodeStatisticsDto> getFlowNodeStatistics(ListViewQueryDto query) {

    final SearchRequest searchRequest;
    if (!query.isFinished()) {
      searchRequest = createQuery(query, ONLY_RUNTIME);
    } else {
      searchRequest = createQuery(query, ALL);
    }
    final Map<String, FlowNodeStatisticsDto> statisticsMap = runQueryAndCollectStats(searchRequest);
    return statisticsMap.values();
  }

  private Map<String, FlowNodeStatisticsDto> runQueryAndCollectStats(SearchRequest searchRequest) {
    try {
      final Map<String, FlowNodeStatisticsDto> statisticsMap = new HashMap<>();
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);

      if (searchResponse.getAggregations() != null) {
        final Children activities = searchResponse.getAggregations().get(AGG_ACTIVITIES);
        CollectionUtil.asMap(
                AGG_ACTIVE_ACTIVITIES, (MapUpdater) FlowNodeStatisticsDto::addActive,
                AGG_INCIDENT_ACTIVITIES, (MapUpdater) FlowNodeStatisticsDto::addIncidents,
                AGG_TERMINATED_ACTIVITIES, (MapUpdater) FlowNodeStatisticsDto::addCanceled,
                AGG_FINISHED_ACTIVITIES, (MapUpdater) FlowNodeStatisticsDto::addCompleted)
            .forEach(
                (aggName, mapUpdater) ->
                    collectStatisticsFor(
                        statisticsMap, activities, aggName, (MapUpdater) mapUpdater));
      }
      return statisticsMap;
    } catch (IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining statistics for activities: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private SearchRequest createQuery(ListViewQueryDto query, ElasticsearchUtil.QueryType queryType) {
    final QueryBuilder q = constantScoreQuery(queryHelper.createQueryFragment(query));

    ChildrenAggregationBuilder agg = children(AGG_ACTIVITIES, ACTIVITIES_JOIN_RELATION);

    if (query.isActive()) {
      agg = agg.subAggregation(getActiveFlowNodesAgg());
    }
    if (query.isCanceled()) {
      agg = agg.subAggregation(getTerminatedActivitiesAgg());
    }
    if (query.isIncidents()) {
      agg = agg.subAggregation(getIncidentActivitiesAgg());
    }
    agg = agg.subAggregation(getFinishedActivitiesAgg());

    LOGGER.debug("Activities statistics request: \n{}\n and aggregation: \n{}", q, agg);

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(listViewTemplate, queryType);

    LOGGER.debug("Search request will search in: \n{}", searchRequest.indices());

    return searchRequest.source(new SearchSourceBuilder().query(q).size(0).aggregation(agg));
  }

  private void collectStatisticsFor(
      Map<String, FlowNodeStatisticsDto> statisticsMap,
      Children activities,
      String aggName,
      MapUpdater mapUpdater) {
    final Filter incidentActivitiesAgg = activities.getAggregations().get(aggName);
    if (incidentActivitiesAgg != null) {
      ((Terms) incidentActivitiesAgg.getAggregations().get(AGG_UNIQUE_ACTIVITIES))
          .getBuckets().stream()
              .forEach(
                  b -> {
                    final String activityId = b.getKeyAsString();
                    final Parent aggregation = b.getAggregations().get(AGG_ACTIVITY_TO_PROCESS);
                    final long docCount = aggregation.getDocCount(); // number of process instances
                    if (statisticsMap.get(activityId) == null) {
                      statisticsMap.put(activityId, new FlowNodeStatisticsDto(activityId));
                    }
                    mapUpdater.updateMapEntry(statisticsMap.get(activityId), docCount);
                  });
    }
  }

  private FilterAggregationBuilder getTerminatedActivitiesAgg() {
    return filter(AGG_TERMINATED_ACTIVITIES, termQuery(ACTIVITY_STATE, FlowNodeState.TERMINATED))
        .subAggregation(
            terms(AGG_UNIQUE_ACTIVITIES)
                .field(ACTIVITY_ID)
                .size(ElasticsearchUtil.TERMS_AGG_SIZE)
                .subAggregation(parent(AGG_ACTIVITY_TO_PROCESS, ACTIVITIES_JOIN_RELATION))
            // we need this to count process instances, not the activity instances
            );
  }

  private FilterAggregationBuilder getActiveFlowNodesAgg() {
    return filter(
            AGG_ACTIVE_ACTIVITIES,
            boolQuery()
                .must(termQuery(INCIDENT, false))
                .must(termQuery(ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())))
        .subAggregation(
            terms(AGG_UNIQUE_ACTIVITIES)
                .field(ACTIVITY_ID)
                .size(ElasticsearchUtil.TERMS_AGG_SIZE)
                .subAggregation(parent(AGG_ACTIVITY_TO_PROCESS, ACTIVITIES_JOIN_RELATION))
            // we need this to count process instances, not the activity instances
            );
  }

  private FilterAggregationBuilder getIncidentActivitiesAgg() {
    return filter(
            AGG_INCIDENT_ACTIVITIES,
            boolQuery()
                .must(termQuery(INCIDENT, true))
                .must(termQuery(ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())))
        .subAggregation(
            terms(AGG_UNIQUE_ACTIVITIES)
                .field(ACTIVITY_ID)
                .size(ElasticsearchUtil.TERMS_AGG_SIZE)
                .subAggregation(parent(AGG_ACTIVITY_TO_PROCESS, ACTIVITIES_JOIN_RELATION))
            // we need this to count process instances, not the activity instances
            );
  }

  private FilterAggregationBuilder getFinishedActivitiesAgg() {
    final QueryBuilder completedEndEventsQ =
        joinWithAnd(
            termQuery(ACTIVITY_TYPE, FlowNodeType.END_EVENT.toString()),
            termQuery(ACTIVITY_STATE, FlowNodeState.COMPLETED.toString()));
    return filter(AGG_FINISHED_ACTIVITIES, completedEndEventsQ)
        .subAggregation(
            terms(AGG_UNIQUE_ACTIVITIES)
                .field(ACTIVITY_ID)
                .size(ElasticsearchUtil.TERMS_AGG_SIZE)
                .subAggregation(parent(AGG_ACTIVITY_TO_PROCESS, ACTIVITIES_JOIN_RELATION))
            // we need this to count process instances, not the activity instances
            );
  }

  @FunctionalInterface
  private interface MapUpdater {
    void updateMapEntry(FlowNodeStatisticsDto statistics, Long value);
  }
}
