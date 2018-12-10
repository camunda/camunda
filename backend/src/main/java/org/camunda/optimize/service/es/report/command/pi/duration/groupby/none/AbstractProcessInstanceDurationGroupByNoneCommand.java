package org.camunda.optimize.service.es.report.command.pi.duration.groupby.none;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.NumberProcessReportResultDto;
import org.camunda.optimize.service.es.report.command.ProcessReportCommand;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;

public abstract class AbstractProcessInstanceDurationGroupByNoneCommand
  extends ProcessReportCommand<NumberProcessReportResultDto> {


  public static final String DURATION_AGGREGATION = "durationAggregation";

  @Override
  protected NumberProcessReportResultDto evaluate() {

    final ProcessReportDataDto processReportData = getProcessReportData();
    logger.debug(
      "Evaluating process instance duration grouped by none report " +
        "for process definition key [{}] and version [{}]",
      processReportData.getProcessDefinitionKey(),
      processReportData.getProcessDefinitionVersion()
    );

    BoolQueryBuilder query = setupBaseQuery(
      processReportData.getProcessDefinitionKey(),
      processReportData.getProcessDefinitionVersion()
    );
    queryFilterEnhancer.addFilterToQuery(query, processReportData.getFilter());

    SearchResponse response = esclient
      .prepareSearch(getOptimizeIndexAliasForType(configurationService.getProcessInstanceType()))
      .setTypes(configurationService.getProcessInstanceType())
      .setQuery(query)
      .setFetchSource(false)
      .setSize(0)
      .addAggregation(createAggregationOperation(ProcessInstanceType.DURATION))
      .get();

    Aggregations aggregations = response.getAggregations();

    NumberProcessReportResultDto numberResult = new NumberProcessReportResultDto();
    numberResult.setResult(processAggregation(aggregations));
    numberResult.setProcessInstanceCount(response.getHits().getTotalHits());
    return numberResult;
  }

  protected abstract long processAggregation(Aggregations aggregations);

  protected abstract AggregationBuilder createAggregationOperation(String fieldName);

}
