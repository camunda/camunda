package org.camunda.optimize.service.es.report.command.process;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.ReportCommand;
import org.elasticsearch.index.query.BoolQueryBuilder;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public abstract class ProcessReportCommand<T extends ProcessReportResultDto> extends ReportCommand<T> {

  protected ProcessQueryFilterEnhancer queryFilterEnhancer;

  @Override
  protected void beforeEvaluate(final CommandContext commandContext) {
    queryFilterEnhancer = (ProcessQueryFilterEnhancer) commandContext.getQueryFilterEnhancer();
  }

  @Override
  protected T filterResultData(final T evaluationResult) {
    return evaluationResult;
  }

  protected ProcessReportDataDto getProcessReportData() {
    return (ProcessReportDataDto) this.reportData;
  }

  protected BoolQueryBuilder setupBaseQuery(String processDefinitionKey, String processDefinitionVersion) {
    BoolQueryBuilder query;
    query = boolQuery()
      .must(termQuery("processDefinitionKey", processDefinitionKey));
    if (!ReportConstants.ALL_VERSIONS.equalsIgnoreCase(processDefinitionVersion)) {
      query = query
        .must(termQuery("processDefinitionVersion", processDefinitionVersion));
    }
    return query;
  }

}
