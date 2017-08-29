package org.camunda.optimize.service.es.filter;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.flownode.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.FilterMapDto;
import org.camunda.optimize.dto.optimize.query.flownode.FlowNodeIdList;
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
    if (filter.getExecutedFlowNodes() != null) {
      addExecutedFlowNodeFilters(query, filter.getExecutedFlowNodes());
    }
  }

  private void addExecutedFlowNodeFilters(BoolQueryBuilder query, ExecutedFlowNodeFilterDto flowNodeFilterDto) {
    List<QueryBuilder> filters = query.filter();
    for (FlowNodeIdList executedFlowNodeId : flowNodeFilterDto.getAndLinkedIds()) {
      filters.add(createFilterQueryBuilder(executedFlowNodeId));
    }
  }

  private QueryBuilder createFilterQueryBuilder(FlowNodeIdList activityIds) {
    return nestedQuery(
      EVENTS,
      createAndFilter(activityIds),
      ScoreMode.None
    );
  }

  private BoolQueryBuilder createAndFilter(FlowNodeIdList flowNodeIdList) {
    BoolQueryBuilder boolQueryBuilder = boolQuery();
    boolQueryBuilder.must(
      createOrFilter(flowNodeIdList.getOrLinkedIds())
    );
    return boolQueryBuilder;
  }

  private BoolQueryBuilder createOrFilter(List<String> orCombinedFlowNodeIds) {
    BoolQueryBuilder boolQueryBuilder = boolQuery();
    for (String activityId : orCombinedFlowNodeIds) {
      boolQueryBuilder.should(
        termQuery(nestedActivityIdFieldLabel(), activityId)
      );
    }
    return boolQueryBuilder;
  }

  private String nestedActivityIdFieldLabel() {
    return EVENTS + "." + ACTIVITY_ID;
  }
}
