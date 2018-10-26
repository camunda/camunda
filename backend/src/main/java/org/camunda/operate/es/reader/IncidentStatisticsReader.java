/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.es.reader;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.types.WorkflowInstanceType;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.dto.incidents.IncidentByWorkflowStatisticsDto;
import org.camunda.operate.rest.dto.incidents.IncidentsByErrorMsgStatisticsDto;
import org.camunda.operate.rest.dto.incidents.IncidentsByWorkflowGroupStatisticsDto;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.apache.lucene.search.join.ScoreMode.None;
import static org.camunda.operate.entities.IncidentState.ACTIVE;
import static org.camunda.operate.es.types.WorkflowInstanceType.ERROR_MSG;
import static org.camunda.operate.es.types.WorkflowInstanceType.INCIDENTS;
import static org.camunda.operate.es.types.WorkflowInstanceType.STATE;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.reverseNested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

@Component
public class IncidentStatisticsReader {

  private static final String INCIDENT_STATE_TERM = String.format("%s.%s", INCIDENTS, STATE);
  private static final String INCIDENT_ERRORMSG_TERM = String.format("%s.%s", INCIDENTS, ERROR_MSG);

  private static final Logger logger = LoggerFactory.getLogger(IncidentStatisticsReader.class);

  @Autowired
  private TransportClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private WorkflowInstanceType workflowInstanceType;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private WorkflowReader workflowReader;

  public Set<IncidentsByWorkflowGroupStatisticsDto> getIncidentStatisticsByWorkflow(){

    // workflowId -> incident stat
    Map<String, IncidentByWorkflowStatisticsDto> statByWorkflowIdMap = getIncidentByWorkflowIdMap();

    //workflow groups
    final Map<String, List<WorkflowEntity>> workflowGroups = workflowReader.getWorkflowsGrouped();

    return collectStatisticsForWorkflowGroups(statByWorkflowIdMap, workflowGroups);

  }

  private Set<IncidentsByWorkflowGroupStatisticsDto> collectStatisticsForWorkflowGroups(Map<String, IncidentByWorkflowStatisticsDto> statByWorkflowIdMap,
    Map<String, List<WorkflowEntity>> workflowGroups) {
    Set<IncidentsByWorkflowGroupStatisticsDto> result = new TreeSet<>(new StatByWorkflowGroupComparator());
    //iterate over workflow groups (bpmnProcessId)
    for (Map.Entry<String, List<WorkflowEntity>> entry: workflowGroups.entrySet()) {
      IncidentsByWorkflowGroupStatisticsDto stat = new IncidentsByWorkflowGroupStatisticsDto();
      stat.setBpmnProcessId(entry.getKey());
      //accumulate stat for workflow group
      long activeInstancesCount = 0;
      long instancesWithActiveIncidentsCount = 0;
      //max version to find out latest workflow name
      long maxVersion = 0;
      //iterate over workflow versions
      for (WorkflowEntity workflowEntity: entry.getValue()) {
        final IncidentByWorkflowStatisticsDto statForWorkflow = statByWorkflowIdMap.get(workflowEntity.getId());
        if (statForWorkflow != null) {
          //accumulate data, even if there are no active incidents
          activeInstancesCount += statForWorkflow.getActiveInstancesCount();
          instancesWithActiveIncidentsCount += statForWorkflow.getInstancesWithActiveIncidentsCount();
          //but add to the list only those with active incidents
          if (statForWorkflow.getInstancesWithActiveIncidentsCount() > 0) {
            statForWorkflow.setName(workflowEntity.getName());
            statForWorkflow.setVersion(workflowEntity.getVersion());
            stat.getWorkflows().add(statForWorkflow);
          }
        }
        //set the latest name
        if (workflowEntity.getVersion() > maxVersion) {
          stat.setWorkflowName(workflowEntity.getName());
          maxVersion = workflowEntity.getVersion();
        }
      }
      //if there are active incidents for a workflow group, include in the result
      if (instancesWithActiveIncidentsCount > 0) {
        stat.setActiveInstancesCount(activeInstancesCount);
        stat.setInstancesWithActiveIncidentsCount(instancesWithActiveIncidentsCount);
        result.add(stat);
      }
    }
    return result;
  }

  /**
   *  Returns incidents statistics by workflowId map (including zeros of instances with incidents)
   * @return
   */
  private Map<String, IncidentByWorkflowStatisticsDto> getIncidentByWorkflowIdMap() {
    Map<String, IncidentByWorkflowStatisticsDto> statByWorkflowIdMap = new HashMap<>();

    QueryBuilder activeInstancesQ = termQuery(WorkflowInstanceType.STATE, WorkflowInstanceState.ACTIVE.toString());

    final String workflowIdsAggName = "workflowIds";
    final String incidentsAggName = "incidents";
    final String activeIncidentsAggName = "active_incidents";
    final String incidentsToInstancesAggName = "incidents_to_instances";
    AggregationBuilder agg =
      terms(workflowIdsAggName).field(WorkflowInstanceType.WORKFLOW_ID).subAggregation(
        nested(incidentsAggName, WorkflowInstanceType.INCIDENTS).subAggregation(
          filter(activeIncidentsAggName, termQuery(INCIDENT_STATE_TERM, ACTIVE.toString())).subAggregation(
            reverseNested(incidentsToInstancesAggName)
          )
        )
      );

    logger.debug("Incident by workflow statistics query: \n{}\n and aggregation: \n{}", activeInstancesQ.toString(), agg.toString());

    final SearchRequestBuilder searchRequestBuilder =
      esClient.prepareSearch(workflowInstanceType.getAlias())
        .setSize(0)
        .setQuery(activeInstancesQ)
        .addAggregation(agg);

    final SearchResponse searchResponse = searchRequestBuilder.get();

    ((Terms)searchResponse.getAggregations().get(workflowIdsAggName))
      .getBuckets().forEach(b -> {
        String workflowId = (String)b.getKey();
        final long runningInstancesCount = b.getDocCount();

        final long instancesWithIncidentsCount = ((ReverseNested) ((Filter) ((Nested) b.getAggregations().get(incidentsAggName)).getAggregations().get(activeIncidentsAggName))
          .getAggregations().get(incidentsToInstancesAggName)).getDocCount();

        final IncidentByWorkflowStatisticsDto incidentByWorkflowStat = new IncidentByWorkflowStatisticsDto(workflowId, instancesWithIncidentsCount,
          runningInstancesCount - instancesWithIncidentsCount);
      statByWorkflowIdMap.put(workflowId, incidentByWorkflowStat);
    });
    return statByWorkflowIdMap;
  }

  public Set<IncidentsByErrorMsgStatisticsDto> getIncidentStatisticsByError() {

    Map<String, WorkflowEntity> workflows = workflowReader.getWorkflows();

    final NestedQueryBuilder withIncidentsQ = nestedQuery(INCIDENTS, termQuery(INCIDENT_STATE_TERM, ACTIVE.toString()), None);

    final String incidentsAggName = "incidents";
    final String activeIncidentsAggName = "activeIncidents";
    final String errorMessagesAggName = "errorMessages";
    final String incidentsToInstancesAggName = "incidents_to_instances";
    final String workflowIdsAggName = "workflowIds";
    AggregationBuilder agg =
      nested(incidentsAggName, WorkflowInstanceType.INCIDENTS).subAggregation(
        filter(activeIncidentsAggName, termQuery(INCIDENT_STATE_TERM, IncidentState.ACTIVE.toString())).subAggregation(
          terms(errorMessagesAggName).field(INCIDENT_ERRORMSG_TERM).subAggregation(
            reverseNested(incidentsToInstancesAggName).subAggregation(
              terms(workflowIdsAggName).field(WorkflowInstanceType.WORKFLOW_ID)     //TODO check if we can put workflowId inside incident entity
            )
          )
        )
      );

    logger.debug("Incident by error message statistics query: \n{}\n and aggregation: \n{}", withIncidentsQ.toString(), agg.toString());

    final SearchRequestBuilder searchRequestBuilder =
      esClient.prepareSearch(workflowInstanceType.getAlias())
        .setSize(0)
        .setQuery(withIncidentsQ)
        .addAggregation(agg);

    final SearchResponse searchResponse = searchRequestBuilder.get();


    Set<IncidentsByErrorMsgStatisticsDto> result = new TreeSet<>(new StatByErrorMsgComparator());

    ((Terms)
      ((Filter)
        ((Nested)searchResponse.getAggregations().get(incidentsAggName))
          .getAggregations().get(activeIncidentsAggName))
            .getAggregations().get(errorMessagesAggName)).getBuckets().forEach(o -> {

        IncidentsByErrorMsgStatisticsDto incidentsByErrorMsgStat = new IncidentsByErrorMsgStatisticsDto(o.getKeyAsString());
        ((Terms)
          ((ReverseNested)o.getAggregations().get(incidentsToInstancesAggName))
            .getAggregations().get(workflowIdsAggName)).getBuckets().forEach(w -> {

            final String workflowId = w.getKeyAsString();
            IncidentByWorkflowStatisticsDto statForWorkflowId = new IncidentByWorkflowStatisticsDto(workflowId, o.getKeyAsString(), w.getDocCount());
            statForWorkflowId.setName(workflows.get(workflowId).getName());
            statForWorkflowId.setVersion(workflows.get(workflowId).getVersion());
            incidentsByErrorMsgStat.getWorkflows().add(statForWorkflowId);
            incidentsByErrorMsgStat.recordInstancesCount(w.getDocCount());
          }
        );
      result.add(incidentsByErrorMsgStat);

    });

    return result;
  }

  public static class StatByErrorMsgComparator implements Comparator<IncidentsByErrorMsgStatisticsDto> {
    @Override
    public int compare(IncidentsByErrorMsgStatisticsDto o1, IncidentsByErrorMsgStatisticsDto o2) {
      if (o1 == null) {
        if (o2 == null) {
          return 0;
        } else {
          return 1;
        }
      }
      if (o2 == null) {
        return -1;
      }
      if (o1.equals(o2)) {
        return 0;
      }
      int result = Long.compare(o2.getInstancesWithErrorCount(), o1.getInstancesWithErrorCount());
      if (result == 0) {
        result = o1.getErrorMessage().compareTo(o2.getErrorMessage());
      }
      return result;
    }
  }

  public static class StatByWorkflowGroupComparator implements Comparator<IncidentsByWorkflowGroupStatisticsDto> {
    @Override
    public int compare(IncidentsByWorkflowGroupStatisticsDto o1, IncidentsByWorkflowGroupStatisticsDto o2) {
      if (o1 == null) {
        if (o2 == null) {
          return 0;
        } else {
          return 1;
        }
      }
      if (o2 == null) {
        return -1;
      }
      if (o1.equals(o2)) {
        return 0;
      }
      int result = Long.compare(o2.getInstancesWithActiveIncidentsCount(), o1.getInstancesWithActiveIncidentsCount());
      if (result == 0) {
        result = o1.getBpmnProcessId().compareTo(o2.getBpmnProcessId());
      }
      return result;
    }
  }
}
