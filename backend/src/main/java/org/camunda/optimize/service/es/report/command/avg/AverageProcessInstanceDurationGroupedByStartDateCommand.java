package org.camunda.optimize.service.es.report.command.avg;

import org.camunda.optimize.dto.optimize.query.report.result.MapReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.service.es.report.command.ReportCommand;
import org.camunda.optimize.service.es.report.command.util.ReportDataUtil;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.metrics.avg.InternalAvg;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class AverageProcessInstanceDurationGroupedByStartDateCommand extends ReportCommand {


  private static final String AVG_DURATION = "avgDuration";
  private static final String DATE_HISTOGRAM_AGGREGATION = "agg";

  @Override
  protected ReportResultDto evaluate() throws IOException, OptimizeException {

    logger.debug("Evaluating average process instance duration grouped by start date report " +
      "for process definition id [{}]", reportData.getProcessDefinitionId());

    BoolQueryBuilder query = setupBaseQuery(reportData.getProcessDefinitionId());
    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());

    SearchResponse response = esclient
      .prepareSearch(configurationService.getOptimizeIndex())
      .setTypes(configurationService.getProcessInstanceType())
      .setQuery(query)
      .setFetchSource(false)
      .setSize(0)
      .addAggregation(createAggregation(reportData.getGroupBy().getUnit()))
      .get();

    MapReportResultDto mapResult = new MapReportResultDto();
    mapResult.setResult(processAggregations(response.getAggregations()));
    return mapResult;
  }

  private Map<String, Long> processAggregations(Aggregations aggregations) {

    Histogram agg = aggregations.get(DATE_HISTOGRAM_AGGREGATION);

    Map<String, Long> result = new HashMap<>();
    // For each entry
    for (Histogram.Bucket entry : agg.getBuckets()) {
      DateTime key = (DateTime) entry.getKey();    // Key
      String formattedDate = key.toString(configurationService.getDateFormat());

      InternalAvg averageDuration = entry.getAggregations().get(AVG_DURATION);
      long roundedDuration = Math.round(averageDuration.getValue());
      result.put(formattedDate, roundedDuration);
    }
    return result;
  }

  private AggregationBuilder createAggregation(String unit) throws OptimizeException {
    DateHistogramInterval interval = ReportDataUtil.getDateHistogramInterval(unit);
    return AggregationBuilders
      .dateHistogram(DATE_HISTOGRAM_AGGREGATION)
      .field(ProcessInstanceType.START_DATE)
      .dateHistogramInterval(interval)
      .subAggregation(
        AggregationBuilders
          .avg(AVG_DURATION)
          .field(ProcessInstanceType.DURATION)
      );
  }

  private BoolQueryBuilder setupBaseQuery(String processDefinitionId) {
    BoolQueryBuilder query;
    query = boolQuery()
      .must(termQuery("processDefinitionId", processDefinitionId));
    return query;
  }
}
