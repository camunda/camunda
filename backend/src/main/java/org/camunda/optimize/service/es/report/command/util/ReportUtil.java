package org.camunda.optimize.service.es.report.command.util;

import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionUpdateDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.metrics.stats.Stats;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;

public class ReportUtil {

  private static final String STATS_AGGREGATION = "minMaxValueOfData";
  private static final Logger logger = LoggerFactory.getLogger(ReportUtil.class);
  public static final int NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION = 80;

  public static void copyReportData(ReportDataDto from, ReportResultDto to) {
    if (to instanceof ProcessReportResultDto) {
      final ProcessReportResultDto processReportResultDto = (ProcessReportResultDto) to;
      processReportResultDto.setData((ProcessReportDataDto) from);
    } else if (to instanceof DecisionReportResultDto) {
      final DecisionReportResultDto decisionReportResultDto = (DecisionReportResultDto) to;
      decisionReportResultDto.setData((DecisionReportDataDto) from);
    } else {
      throw new IllegalStateException("Unsupported result dto: " + to.getClass().getSimpleName());
    }
  }

  public static DateHistogramInterval getDateHistogramInterval(GroupByDateUnit interval,
                                                               RestHighLevelClient esClient,
                                                               QueryBuilder query,
                                                               String esType,
                                                               String field) throws OptimizeException {
    switch (interval) {
      case YEAR:
        return DateHistogramInterval.YEAR;
      case MONTH:
        return DateHistogramInterval.MONTH;
      case WEEK:
        return DateHistogramInterval.WEEK;
      case DAY:
        return DateHistogramInterval.DAY;
      case HOUR:
        return DateHistogramInterval.HOUR;
      case AUTOMATIC:
        return automaticIntervalSelection(esClient, query, esType, field);
      default:
        logger.error("Unknown interval {}. Please state a valid interval", interval);
        throw new OptimizeException("Unknown interval used. Please state a valid interval.");
    }
  }

  private static DateHistogramInterval automaticIntervalSelection(RestHighLevelClient esClient,
                                                                  QueryBuilder query,
                                                                  String esType,
                                                                  String field) {
    Stats agg = getMinMaxStats(esClient, query, esType, field);
    double min = agg.getMin();
    double max = agg.getMax();
    long count = agg.getCount();

    if (count > 1) {
      long msAsUnit = Math.round((max - min) / (NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION - 1));
      return new DateHistogramInterval(msAsUnit + "ms");
    } else {
      return DateHistogramInterval.MONTH;
    }
  }

  private static Stats getMinMaxStats(RestHighLevelClient esClient, QueryBuilder query, String esType, String field) {
    AggregationBuilder statsAgg = AggregationBuilders
      .stats(STATS_AGGREGATION)
      .field(field);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(statsAgg)
      .size(0);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(esType))
        .types(esType)
        .source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = "Could not automatically determine interval of group by start date!";
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    return response.getAggregations().get(STATS_AGGREGATION);
  }

  public static void copyCombinedReportMetaData(CombinedReportDefinitionDto from, CombinedReportDefinitionDto to) {
    copyMetaData(from, to);
    to.setData(from.getData());
  }

  public static void copyMetaData(ReportDefinitionDto from, ReportDefinitionDto to) {
    to.setId(from.getId());
    to.setName(from.getName());
    to.setOwner(from.getOwner());
    to.setCreated(from.getCreated());
    to.setLastModifier(from.getLastModifier());
    to.setLastModified(from.getLastModified());
  }

  public static void copyDefinitionMetaDataToUpdate(ReportDefinitionDto from, ReportDefinitionUpdateDto to) {
    to.setId(from.getId());
    to.setName(from.getName());
    to.setOwner(from.getOwner());
    to.setLastModifier(from.getLastModifier());
    to.setLastModified(from.getLastModified());
  }

  public static <O extends Combinable> boolean isCombinable(O o1, O o2) {
    if (o1 == null && o2 == null) {
      return true;
    } else if (o1 == null) {
      return false;
    } else if (o2 == null) {
      return false;
    } else {
      return o1.isCombinable(o2);
    }
  }
}
