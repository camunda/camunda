package org.camunda.optimize.service.es.report.command.process.flownode.duration;

import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.ParsedTDigestPercentiles;

import static org.elasticsearch.search.aggregations.AggregationBuilders.percentiles;

public class MedianFlowNodeDurationByFlowNodeCommand extends AbstractFlowNodeDurationByFlowNodeCommand<ParsedTDigestPercentiles> {

  @Override
  protected AggregationBuilder addOperation(String aggregationName, String field) {
    return percentiles(aggregationName)
                  .percentiles(50)
                  .field(field);
  }

  @Override
  protected Long processOperationAggregation(ParsedTDigestPercentiles aggregation) {
    double median = aggregation.percentile(50);
    if (Double.isNaN(median) || Double.isInfinite(median)){
      return 0L;
    } else {
      return Math.round(median);
    }
  }
}
