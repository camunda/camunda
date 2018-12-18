package org.camunda.optimize.service.es.report.command.process.processinstance.frequency;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportNumberResultDto;
import org.camunda.optimize.service.es.report.command.process.ProcessReportCommand;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;

public class CountProcessInstanceFrequencyGroupByNoneCommand
  extends ProcessReportCommand<ProcessReportNumberResultDto> {

  @Override
  protected ProcessReportNumberResultDto evaluate() {

    final ProcessReportDataDto processReportData = getProcessReportData();
    logger.debug(
      "Evaluating count process instance frequency grouped by none report " +
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
      .get();

    ProcessReportNumberResultDto numberResult = new ProcessReportNumberResultDto();
    numberResult.setResult(response.getHits().getTotalHits());
    numberResult.setProcessInstanceCount(response.getHits().getTotalHits());
    return numberResult;
  }

}
