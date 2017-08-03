package org.camunda.optimize.service.es.filter;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.FilterMapDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class ExecutedFlowNodeFilter implements QueryFilter {

  public void addFilters(BoolQueryBuilder query, FilterMapDto filter) {
    addExecutedFlowNodeFilters(query, filter.getExecutedFlowNodeIds());
  }

  private void addExecutedFlowNodeFilters(BoolQueryBuilder query, List<String> executedFlowNodeIds) {
    if (executedFlowNodeIds != null) {
      List<QueryBuilder> filters = query.filter();
      for (String executedFlowNodeId : executedFlowNodeIds) {

        filters.add(createFilterQueryBuilder(executedFlowNodeId));
      }
    }
  }

  private QueryBuilder createFilterQueryBuilder(String activityId) {
    return nestedQuery(
      EVENTS,
      createBoolFilterForAllActivityIds(activityId),
      ScoreMode.None
    );
  }

  private BoolQueryBuilder createBoolFilterForAllActivityIds(String activityId) {
    BoolQueryBuilder boolQueryBuilder = boolQuery();
//    for (String activityId : activityIds) {
      boolQueryBuilder.must(
        termQuery(nestedActivityIdFieldLabel(), activityId)
      );
//    }
    return boolQueryBuilder;
  }

  private String nestedActivityIdFieldLabel() {
    return EVENTS + "." + ACTIVITY_ID;
  }
}
