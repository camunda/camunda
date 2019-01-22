package org.camunda.optimize.service.es.report.command.process.processinstance.frequency;

import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.StartDateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.service.es.report.command.process.ProcessReportCommand;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportUtil.getDateHistogramInterval;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.START_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;

public class CountProcessInstanceFrequencyByStartDateCommand extends ProcessReportCommand<ProcessReportMapResultDto> {

  private static final String DATE_HISTOGRAM_AGGREGATION = "dateIntervalGrouping";

  @Override
  protected ProcessReportMapResultDto evaluate() throws OptimizeException {

    final ProcessReportDataDto processReportData = getProcessReportData();
    logger.debug(
      "Evaluating count process instance frequency grouped by start date report " +
        "for process definition key [{}] and version [{}]",
      processReportData.getProcessDefinitionKey(),
      processReportData.getProcessDefinitionVersion()
    );

    BoolQueryBuilder query = setupBaseQuery(
      processReportData.getProcessDefinitionKey(),
      processReportData.getProcessDefinitionVersion()
    );
    queryFilterEnhancer.addFilterToQuery(query, processReportData.getFilter());

    StartDateGroupByValueDto groupByStartDate = ((StartDateGroupByDto) processReportData.getGroupBy()).getValue();

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(createAggregation(groupByStartDate.getUnit(), query))
      .size(0);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(PROC_INSTANCE_TYPE))
        .types(PROC_INSTANCE_TYPE)
        .source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason =
        String.format(
          "Could not evaluate count process instance frequency grouped by start date report " +
            "for process definition with key [%s] and version [%s]",
          processReportData.getProcessDefinitionKey(),
          processReportData.getProcessDefinitionVersion()
        );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    ProcessReportMapResultDto mapResult = new ProcessReportMapResultDto();
    mapResult.setResult(processAggregations(response.getAggregations()));
    mapResult.setProcessInstanceCount(response.getHits().getTotalHits());
    return mapResult;
  }

  private AggregationBuilder createAggregation(GroupByDateUnit unit, QueryBuilder query) throws OptimizeException {
    DateHistogramInterval interval = getDateHistogramInterval(unit, esClient, query, PROC_INSTANCE_TYPE, START_DATE);
    return AggregationBuilders
      .dateHistogram(DATE_HISTOGRAM_AGGREGATION)
      .order(BucketOrder.key(false))
      .field(START_DATE)
      .dateHistogramInterval(interval);
  }

  private Map<String, Long> processAggregations(Aggregations aggregations) {
    Histogram agg = aggregations.get(DATE_HISTOGRAM_AGGREGATION);

    Map<String, Long> result = new LinkedHashMap<>();
    // For each entry
    for (Histogram.Bucket entry : agg.getBuckets()) {
      DateTime key = (DateTime) entry.getKey();    // Key
      long docCount = entry.getDocCount();         // Doc count
      String formattedDate = key.withZone(DateTimeZone.getDefault()).toString(OPTIMIZE_DATE_FORMAT);
      result.put(formattedDate, docCount);
    }
    return result;
  }

}
