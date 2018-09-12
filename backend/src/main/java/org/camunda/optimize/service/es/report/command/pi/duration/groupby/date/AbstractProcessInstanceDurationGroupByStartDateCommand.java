package org.camunda.optimize.service.es.report.command.pi.duration.groupby.date;

import org.camunda.optimize.dto.optimize.query.report.single.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.value.StartDateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapSingleReportResultDto;
import org.camunda.optimize.service.es.report.command.ReportCommand;
import org.camunda.optimize.service.es.report.command.util.ReportUtil;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.joda.time.DateTime;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractProcessInstanceDurationGroupByStartDateCommand
    extends ReportCommand<MapSingleReportResultDto> {

  protected static final String DURATION_AGGREGATION = "durationAggregation";
  private static final String DATE_HISTOGRAM_AGGREGATION = "dateIntervalGrouping";

  @Override
  protected MapSingleReportResultDto evaluate() throws OptimizeException {

    logger.debug("Evaluating process instance duration grouped by start date report " +
      "for process definition key [{}] and version [{}]",
      reportData.getProcessDefinitionKey(),
      reportData.getProcessDefinitionVersion());

    BoolQueryBuilder query = setupBaseQuery(
        reportData.getProcessDefinitionKey(),
        reportData.getProcessDefinitionVersion()
    );

    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());

    StartDateGroupByValueDto groupByStartDate = ((StartDateGroupByDto) reportData.getGroupBy()).getValue();

    SearchResponse response = esclient
      .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
      .setTypes(configurationService.getProcessInstanceType())
      .setQuery(query)
      .setFetchSource(false)
      .setSize(0)
      .addAggregation(createAggregation(groupByStartDate.getUnit()))
      .get();


    MapSingleReportResultDto mapResult = new MapSingleReportResultDto();
    mapResult.setResult(processAggregations(response.getAggregations()));
    mapResult.setProcessInstanceCount(response.getHits().getTotalHits());
    return mapResult;
  }

  private Map<String, Long> processAggregations(Aggregations aggregations) {

    Histogram agg = aggregations.get(DATE_HISTOGRAM_AGGREGATION);

    Map<String, Long> result = new LinkedHashMap<>();
    // For each entry
    for (Histogram.Bucket entry : agg.getBuckets()) {
      DateTime key = (DateTime) entry.getKey();    // Key
      String formattedDate = key.toString(configurationService.getOptimizeDateFormat());

      long roundedDuration = processAggregationOperation(entry.getAggregations());
      result.put(formattedDate, roundedDuration);
    }
    return result;
  }

  private AggregationBuilder createAggregation(String unit) throws OptimizeException {
    DateHistogramInterval interval = ReportUtil.getDateHistogramInterval(unit);
    return AggregationBuilders
      .dateHistogram(DATE_HISTOGRAM_AGGREGATION)
      .field(ProcessInstanceType.START_DATE)
      .order(BucketOrder.key(false))
      .dateHistogramInterval(interval)
      .subAggregation(
        createAggregationOperation()
      );
  }

  protected abstract long processAggregationOperation(Aggregations aggregations);

  protected abstract AggregationBuilder createAggregationOperation();


}
