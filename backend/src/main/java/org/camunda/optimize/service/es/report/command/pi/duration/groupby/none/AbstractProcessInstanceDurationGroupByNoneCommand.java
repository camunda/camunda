package org.camunda.optimize.service.es.report.command.pi.duration.groupby.none;

import org.camunda.optimize.dto.optimize.query.report.single.result.NumberSingleReportResultDto;
import org.camunda.optimize.service.es.report.command.ReportCommand;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;

public abstract class AbstractProcessInstanceDurationGroupByNoneCommand<AGG extends Aggregation>
    extends ReportCommand<NumberSingleReportResultDto> {


  public static final String DURATION_AGGREGATION = "durationAggregation";

  @Override
  protected NumberSingleReportResultDto evaluate() {

    logger.debug("Evaluating process instance duration grouped by none report " +
      "for process definition key [{}] and version [{}]",
      reportData.getProcessDefinitionKey(),
      reportData.getProcessDefinitionVersion());

    BoolQueryBuilder query = setupBaseQuery(
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
      .addAggregation(createAggregationOperation(DURATION_AGGREGATION, ProcessInstanceType.DURATION))
      .get();

    AGG aggregation = response.getAggregations().get(DURATION_AGGREGATION);

    NumberSingleReportResultDto numberResult = new NumberSingleReportResultDto();
    numberResult.setResult(processAggregation(aggregation));
    numberResult.setProcessInstanceCount(response.getHits().getTotalHits());
    return numberResult;
  }

  protected abstract long processAggregation(AGG aggregation);

  protected abstract AggregationBuilder createAggregationOperation(String aggregationName, String fieldName);

}
