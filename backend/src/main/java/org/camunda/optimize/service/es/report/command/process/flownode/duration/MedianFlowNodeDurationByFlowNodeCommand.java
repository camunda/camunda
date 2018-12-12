package org.camunda.optimize.service.es.report.command.process.flownode.duration;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.InternalTDigestPercentiles;

import static org.elasticsearch.search.aggregations.AggregationBuilders.percentiles;

public class MedianFlowNodeDurationByFlowNodeCommand extends AbstractFlowNodeDurationByFlowNodeCommand<InternalTDigestPercentiles> {

  @Override
  protected AggregationBuilder addOperation(String aggregationName, String field) {
    return percentiles(aggregationName)
                  .percentiles(50)
                  .field(field);
  }

  @Override
  protected Long processOperationAggregation(InternalTDigestPercentiles aggregation) {
    return Math.round(aggregation.percentile(50));
  }
}
