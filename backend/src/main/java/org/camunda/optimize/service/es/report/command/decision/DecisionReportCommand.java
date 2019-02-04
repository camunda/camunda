package org.camunda.optimize.service.es.report.command.decision;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.service.es.filter.DecisionQueryFilterEnhancer;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.ReportCommand;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.es.report.result.ReportResult;
import org.elasticsearch.index.query.BoolQueryBuilder;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public abstract class DecisionReportCommand<T extends ReportResult> extends ReportCommand<T> {
  protected DecisionQueryFilterEnhancer queryFilterEnhancer;
  protected IntervalAggregationService intervalAggregationService;

  @Override
  protected void beforeEvaluate(final CommandContext commandContext) {
    intervalAggregationService = commandContext.getIntervalAggregationService();
    queryFilterEnhancer = (DecisionQueryFilterEnhancer) commandContext.getQueryFilterEnhancer();
  }

  @Override
  protected T filterResultData(final T evaluationResult) {
    return evaluationResult;
  }

  protected DecisionReportDataDto getDecisionReportData() {
    return (DecisionReportDataDto) this.reportData;
  }

  protected BoolQueryBuilder setupBaseQuery(String decisionDefinitionKey, String decisionDefinitionVersion) {
    BoolQueryBuilder query;
    query = boolQuery()
      .must(termQuery("decisionDefinitionKey", decisionDefinitionKey));
    if (!ReportConstants.ALL_VERSIONS.equalsIgnoreCase(decisionDefinitionVersion)) {
      query = query
        .must(termQuery("decisionDefinitionVersion", decisionDefinitionVersion));
    }
    return query;
  }

}
