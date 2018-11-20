package org.camunda.optimize.service.es.report.command.pi.frequency;

import org.camunda.optimize.dto.optimize.query.report.single.process.result.NumberProcessReportResultDto;
import org.camunda.optimize.service.es.report.command.ReportCommand;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;

public class CountProcessInstanceFrequencyGroupByNoneCommand extends ReportCommand<NumberProcessReportResultDto> {

  @Override
  protected NumberProcessReportResultDto evaluate() {

    logger.debug("Evaluating count process instance frequency grouped by none report " +
      "for process definition key [{}] and version [{}]",
      reportData.getProcessDefinitionKey(),
      reportData.getProcessDefinitionVersion());

    BoolQueryBuilder query = setupBaseQuery(
        reportData.getProcessDefinitionKey(),
        reportData.getProcessDefinitionVersion()
    );
    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());

    SearchResponse response = esclient
      .prepareSearch(getOptimizeIndexAliasForType(configurationService.getProcessInstanceType()))
      .setTypes(configurationService.getProcessInstanceType())
      .setQuery(query)
      .setFetchSource(false)
      .setSize(0)
      .get();

    NumberProcessReportResultDto numberResult = new NumberProcessReportResultDto();
    numberResult.setResult(response.getHits().getTotalHits());
    numberResult.setProcessInstanceCount(response.getHits().getTotalHits());
    return numberResult;
  }

}
