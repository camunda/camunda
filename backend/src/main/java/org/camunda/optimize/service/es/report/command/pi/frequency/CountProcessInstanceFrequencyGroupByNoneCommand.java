package org.camunda.optimize.service.es.report.command.pi.frequency;

import org.camunda.optimize.dto.optimize.query.report.single.result.NumberSingleReportResultDto;
import org.camunda.optimize.service.es.report.command.ReportCommand;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;

public class CountProcessInstanceFrequencyGroupByNoneCommand extends ReportCommand<NumberSingleReportResultDto> {

  @Override
  protected NumberSingleReportResultDto evaluate() {

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
      .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
      .setTypes(configurationService.getProcessInstanceType())
      .setQuery(query)
      .setFetchSource(false)
      .setSize(0)
      .get();

    NumberSingleReportResultDto numberResult = new NumberSingleReportResultDto();
    numberResult.setResult(response.getHits().getTotalHits());
    numberResult.setProcessInstanceCount(response.getHits().getTotalHits());
    return numberResult;
  }

}
