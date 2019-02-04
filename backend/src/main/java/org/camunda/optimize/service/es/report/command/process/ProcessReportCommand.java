package org.camunda.optimize.service.es.report.command.process;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.ReportCommand;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.es.report.result.ReportResult;
import org.elasticsearch.index.query.BoolQueryBuilder;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public abstract class ProcessReportCommand<T  extends ReportResult> extends ReportCommand<T> {

  private ProcessQueryFilterEnhancer queryFilterEnhancer;
  protected IntervalAggregationService intervalAggregationService;

  @Override
  public void beforeEvaluate(final CommandContext commandContext) {
    intervalAggregationService = commandContext.getIntervalAggregationService();
    queryFilterEnhancer = (ProcessQueryFilterEnhancer) commandContext.getQueryFilterEnhancer();
  }

  @Override
  protected T filterResultData(final T evaluationResult) {
    return evaluationResult;
  }

  protected ProcessReportDataDto getProcessReportData() {
    return (ProcessReportDataDto) this.reportData;
  }

  public BoolQueryBuilder setupBaseQuery(ProcessReportDataDto processReportData) {
    String processDefinitionKey = processReportData.getProcessDefinitionKey();
    String processDefinitionVersion = processReportData.getProcessDefinitionVersion();
    BoolQueryBuilder query;
    query = boolQuery()
      .must(termQuery("processDefinitionKey", processDefinitionKey));
    if (!ReportConstants.ALL_VERSIONS.equalsIgnoreCase(processDefinitionVersion)) {
      query = query
        .must(termQuery("processDefinitionVersion", processDefinitionVersion));
    }
    queryFilterEnhancer.addFilterToQuery(query, processReportData.getFilter());
    return query;
  }

}
