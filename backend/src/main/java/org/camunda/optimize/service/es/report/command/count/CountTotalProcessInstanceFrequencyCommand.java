package org.camunda.optimize.service.es.report.command.count;

import org.camunda.optimize.dto.optimize.query.report.result.NumberReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.service.es.report.command.ReportCommand;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;

import java.io.IOException;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class CountTotalProcessInstanceFrequencyCommand extends ReportCommand {


    @Override
  protected ReportResultDto evaluate() throws IOException, OptimizeException {

    logger.debug("Evaluating count process instance frequency grouped by none report " +
      "for process definition id [{}]", reportData.getProcessDefinitionId());

    BoolQueryBuilder query = setupBaseQuery(reportData.getProcessDefinitionId());
    queryFilterEnhancer.addFilterToQuery(query, reportData.getFilter());

    SearchResponse response = esclient
      .prepareSearch(configurationService.getOptimizeIndex(configurationService.getProcessInstanceType()))
      .setTypes(configurationService.getProcessInstanceType())
      .setQuery(query)
      .setFetchSource(false)
      .setSize(0)
      .get();

    NumberReportResultDto numberResult = new NumberReportResultDto();
    numberResult.setResult(response.getHits().getTotalHits());
    return numberResult;
  }

  private BoolQueryBuilder setupBaseQuery(String processDefinitionId) {
    BoolQueryBuilder query;
    query = boolQuery()
      .must(termQuery("processDefinitionId", processDefinitionId));
    return query;
  }
}
