package org.camunda.optimize.service.es.report.command.avg;

import org.camunda.optimize.dto.optimize.query.report.result.NumberReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.service.es.report.command.ReportCommand;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.avg.InternalAvg;

import java.io.IOException;

public class AverageTotalProcessInstanceDurationCommand extends ReportCommand {


  public static final String AVG_DURATION = "avgDuration";

  @Override
  protected ReportResultDto evaluate() {

    logger.debug("Evaluating average process instance duration grouped by none report " +
      "for process definition id [{}]", reportData.getProcessDefinitionId());

    BoolQueryBuilder query = setupBaseQuery(
        reportData.getProcessDefinitionId(),
        reportData.getProcessDefinitionKey(),
        reportData.getProcessDefinitionVersion()
    );
    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());

    SearchResponse response = esclient
      .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
      .setTypes(configurationService.getProcessInstanceType())
      .setQuery(query)
      .setFetchSource(false)
      .setSize(0)
      .addAggregation(createAggregation())
      .get();

    NumberReportResultDto numberResult = new NumberReportResultDto();
    numberResult.setResult(processAggregations(response.getAggregations()));
    return numberResult;
  }

  private long processAggregations(Aggregations aggregations) {
    InternalAvg averageDuration = aggregations.get(AVG_DURATION);
    long roundedDuration = Math.round(averageDuration.getValue());
    return roundedDuration;
  }

  private AggregationBuilder createAggregation() {
    return AggregationBuilders
      .avg(AVG_DURATION)
      .field(ProcessInstanceType.DURATION);
  }

}
