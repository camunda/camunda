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
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITY_ID;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITY_STATE;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITY_TYPE;
import static io.camunda.operate.schema.templates.ListViewTemplate.INCIDENT;
import static io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations.TERMS_AGG_SIZE;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.children;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.parent;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.termAggregation;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.withSubaggregations;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.constantScore;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.withTenantCheck;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.Convertable;
import io.camunda.operate.util.MapPath;
import io.camunda.operate.webapp.opensearch.OpenSearchQueryHelper;
import io.camunda.operate.webapp.reader.FlowNodeStatisticsReader;
import io.camunda.operate.webapp.rest.dto.FlowNodeStatisticsDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchFlowNodeStatisticsReader implements FlowNodeStatisticsReader {

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private OpenSearchQueryHelper openSearchQueryHelper;

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Override
  public Collection<FlowNodeStatisticsDto> getFlowNodeStatistics(ListViewQueryDto query) {
    SearchRequest.Builder searchRequest;

    if (!query.isFinished()) {
      searchRequest = createQuery(query, RequestDSL.QueryType.ONLY_RUNTIME);
    } else {
      searchRequest = createQuery(query, RequestDSL.QueryType.ALL);
    }

    Map<String, FlowNodeStatisticsDto> statisticsMap = runQueryAndCollectStats(searchRequest);
    return statisticsMap.values();
  }

  private SearchRequest.Builder createQuery(
      ListViewQueryDto query, RequestDSL.QueryType queryType) {
    Map<String, Aggregation> subAggregations = new HashMap<>();
    if (query.isActive()) {
      subAggregations.put(AGG_ACTIVE_ACTIVITIES, getActiveFlowNodesAggregation());
    }
    if (query.isCanceled()) {
      subAggregations.put(AGG_TERMINATED_ACTIVITIES, getTerminatedActivitiesAggregation());
    }
    if (query.isIncidents()) {
      subAggregations.put(AGG_INCIDENT_ACTIVITIES, getIncidentActivitiesAggregation());
    }
    subAggregations.put(AGG_FINISHED_ACTIVITIES, getFinishedActivitiesAggregation());

    return searchRequestBuilder(listViewTemplate, queryType)
        .query(
            withTenantCheck(
                constantScore(openSearchQueryHelper.createQueryFragment(query, queryType))))
        .size(0)
        .aggregations(
            AGG_ACTIVITIES,
            withSubaggregations(children(ACTIVITIES_JOIN_RELATION), subAggregations));
  }

  private Aggregation getTerminatedActivitiesAggregation() {
    return withSubaggregations(
        term(ACTIVITY_STATE, FlowNodeState.TERMINATED.name()), uniqueActivitiesAggregation());
  }

  private Aggregation getActiveFlowNodesAggregation() {
    return withSubaggregations(
        and(term(INCIDENT, false), term(ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())),
        uniqueActivitiesAggregation());
  }

  private Aggregation getIncidentActivitiesAggregation() {
    return withSubaggregations(
        and(term(INCIDENT, true), term(ACTIVITY_STATE, FlowNodeState.ACTIVE.toString())),
        uniqueActivitiesAggregation());
  }

  private Aggregation getFinishedActivitiesAggregation() {
    return withSubaggregations(
        and(
            term(ACTIVITY_TYPE, FlowNodeType.END_EVENT.toString()),
            term(ACTIVITY_STATE, FlowNodeState.COMPLETED.toString())),
        uniqueActivitiesAggregation());
  }

  private Map<String, Aggregation> uniqueActivitiesAggregation() {
    return Map.of(
        AGG_UNIQUE_ACTIVITIES,
        withSubaggregations(
            termAggregation(ACTIVITY_ID, TERMS_AGG_SIZE),
            Map.of(AGG_ACTIVITY_TO_PROCESS, parent(ACTIVITIES_JOIN_RELATION)._toAggregation())));
  }

  private Map<String, FlowNodeStatisticsDto> runQueryAndCollectStats(
      SearchRequest.Builder searchRequest) {
    Map<String, FlowNodeStatisticsDto> statisticsMap = new HashMap<>();
    Map<String, Object> result = richOpenSearchClient.doc().searchAsMap(searchRequest);
    Optional<Map<String, Object>> maybeActivities =
        MapPath.from(result)
            .getByPath("aggregations", "children#activities")
            .flatMap(Convertable::to);

    maybeActivities.ifPresent(
        activities ->
            CollectionUtil.asMap(
                    AGG_ACTIVE_ACTIVITIES,
                        (OpensearchFlowNodeStatisticsReader.MapUpdater)
                            FlowNodeStatisticsDto::addActive,
                    AGG_INCIDENT_ACTIVITIES,
                        (OpensearchFlowNodeStatisticsReader.MapUpdater)
                            FlowNodeStatisticsDto::addIncidents,
                    AGG_TERMINATED_ACTIVITIES,
                        (OpensearchFlowNodeStatisticsReader.MapUpdater)
                            FlowNodeStatisticsDto::addCanceled,
                    AGG_FINISHED_ACTIVITIES,
                        (OpensearchFlowNodeStatisticsReader.MapUpdater)
                            FlowNodeStatisticsDto::addCompleted)
                .forEach(
                    (aggName, mapUpdater) ->
                        collectStatisticsFor(
                            statisticsMap,
                            activities,
                            aggName,
                            (OpensearchFlowNodeStatisticsReader.MapUpdater) mapUpdater)));
    return statisticsMap;

    /* Original implementation */

    //    Map<String, FlowNodeStatisticsDto> statisticsMap = new HashMap<>();
    //    var aggregations = richOpenSearchClient.doc().searchAggregations(searchRequest);
    //    if (aggregations != null) {
    //      var activities = aggregations.get(AGG_ACTIVITIES);
    //      CollectionUtil.asMap(
    //        AGG_ACTIVE_ACTIVITIES, (OpensearchFlowNodeStatisticsReader.MapUpdater)
    // FlowNodeStatisticsDto::addActive,
    //        AGG_INCIDENT_ACTIVITIES, (OpensearchFlowNodeStatisticsReader.MapUpdater)
    // FlowNodeStatisticsDto::addIncidents,
    //        AGG_TERMINATED_ACTIVITIES, (OpensearchFlowNodeStatisticsReader.MapUpdater)
    // FlowNodeStatisticsDto::addCanceled,
    //        AGG_FINISHED_ACTIVITIES, (OpensearchFlowNodeStatisticsReader.MapUpdater)
    // FlowNodeStatisticsDto::addCompleted)
    //        .forEach((aggName, mapUpdater) -> collectStatisticsFor(statisticsMap, activities,
    // aggName, (OpensearchFlowNodeStatisticsReader.MapUpdater) mapUpdater));
    //    }
    //    return statisticsMap;
  }

  private void collectStatisticsFor(
      Map<String, FlowNodeStatisticsDto> statisticsMap,
      Map<String, Object> activities,
      String aggName,
      MapUpdater mapUpdater) {
    Optional<List<Map<String, Object>>> maybeUniqueActivitiesBuckets =
        MapPath.from(activities)
            .getByPath("filter#" + aggName, "sterms#" + AGG_UNIQUE_ACTIVITIES, "buckets")
            .flatMap(Convertable::to);

    maybeUniqueActivitiesBuckets.ifPresent(
        buckets ->
            buckets.forEach(
                bucket -> {
                  String activityId = (String) bucket.get("key");
                  final long docCount =
                      (Integer)
                          MapPath.from(bucket)
                              .getByPath("parent#" + AGG_ACTIVITY_TO_PROCESS, "doc_count")
                              .flatMap(Convertable::to)
                              .get(); // number of process instances

                  statisticsMap.putIfAbsent(activityId, new FlowNodeStatisticsDto(activityId));
                  mapUpdater.updateMapEntry(statisticsMap.get(activityId), docCount);
                }));

    /* Original implementation */

    //    var incidentsActivitiesAggregation = activities.children().aggregations().get(aggName);
    //    if(incidentsActivitiesAggregation != null){
    //        var uniqueActivities =
    // incidentsActivitiesAggregation.filter().aggregations().get(AGG_UNIQUE_ACTIVITIES);
    //        uniqueActivities.sterms().buckets().array().forEach(b -> {
    //          String activityId = b.key();
    //          var aggregation = b.aggregations().get(AGG_ACTIVITY_TO_PROCESS);
    //          final long docCount = aggregation.topHits().hits().total().value();  //number of
    // process instances
    //          if (statisticsMap.get(activityId) == null) {
    //            statisticsMap.put(activityId, new FlowNodeStatisticsDto(activityId));
    //          }
    //          mapUpdater.updateMapEntry(statisticsMap.get(activityId), docCount);
    //        });
    //    }
  }

  @FunctionalInterface
  private interface MapUpdater {
    void updateMapEntry(FlowNodeStatisticsDto statistics, Long value);
  }
}
