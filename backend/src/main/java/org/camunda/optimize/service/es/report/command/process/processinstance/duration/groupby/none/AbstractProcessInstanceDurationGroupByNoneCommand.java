package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportNumberResultDto;
import org.camunda.optimize.service.es.report.command.process.ProcessReportCommand;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;

public abstract class AbstractProcessInstanceDurationGroupByNoneCommand
  extends ProcessReportCommand<ProcessReportNumberResultDto> {


  public static final String DURATION_AGGREGATION = "durationAggregation";

  @Override
  protected ProcessReportNumberResultDto evaluate() {

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
      .prepareSearch(getOptimizeIndexAliasForType(ElasticsearchConstants.PROC_INSTANCE_TYPE))
      .setTypes(ElasticsearchConstants.PROC_INSTANCE_TYPE)
      .setQuery(query)
      .setFetchSource(false)
      .setSize(0)
      .addAggregation(createAggregationOperation(ProcessInstanceType.DURATION))
      .get();

    Aggregations aggregations = response.getAggregations();

    ProcessReportNumberResultDto numberResult = new ProcessReportNumberResultDto();
    numberResult.setResult(processAggregation(aggregations));
    numberResult.setProcessInstanceCount(response.getHits().getTotalHits());
    return numberResult;
  }

  protected abstract long processAggregation(Aggregations aggregations);

  protected abstract AggregationBuilder createAggregationOperation(String fieldName);

}
