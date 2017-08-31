package org.camunda.optimize.service.es.filter;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.FilterMapDto;
import org.camunda.optimize.dto.optimize.query.flownode.ExecutedFlowNodeFilterDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.ACTIVITY_ID;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.EVENTS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class ExecutedFlowNodeFilter implements QueryFilter {

  private Logger logger = LoggerFactory.getLogger(ExecutedFlowNodeFilter.class);

  public void addFilters(BoolQueryBuilder query, FilterMapDto filter) {
    if (filter.getExecutedFlowNodes() != null) {
      addExecutedFlowNodeFilters(query, filter.getExecutedFlowNodes());
    }
  }

  private void addExecutedFlowNodeFilters(BoolQueryBuilder query, List<ExecutedFlowNodeFilterDto> flowNodeFilter) {
    List<QueryBuilder> filters = query.filter();
    for (ExecutedFlowNodeFilterDto executedFlowNode : flowNodeFilter) {
      filters.add(createFilterQueryBuilder(executedFlowNode));
    }
  }

  private QueryBuilder createFilterQueryBuilder(ExecutedFlowNodeFilterDto flowNodeFilter) {
    BoolQueryBuilder boolQueryBuilder = boolQuery();
    if (flowNodeFilter.getOperator().equals("=")) {
      for (String value : flowNodeFilter.getValues()) {
        boolQueryBuilder.should(
          nestedQuery(
            EVENTS,
            termQuery(nestedActivityIdFieldLabel(), value),
            ScoreMode.None
          )
        );
      }
    } else if (flowNodeFilter.getOperator().equals("!=")) {
      for (String value : flowNodeFilter.getValues()) {
        boolQueryBuilder.mustNot(
          nestedQuery(
            EVENTS,
            termQuery(nestedActivityIdFieldLabel(), value),
            ScoreMode.None
          )
        );
      }
    } else {
      logger.error("Could not filter for flow nodes. " +
        "Operator [{}] is not allowed! Use either [=] or [!=]", flowNodeFilter.getOperator());
    }
    return boolQueryBuilder;
  }

  private String nestedActivityIdFieldLabel() {
    return EVENTS + "." + ACTIVITY_ID;
  }
}
