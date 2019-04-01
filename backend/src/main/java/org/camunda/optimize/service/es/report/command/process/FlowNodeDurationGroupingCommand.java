package org.camunda.optimize.service.es.report.command.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapDurationReportResult;

public abstract class FlowNodeDurationGroupingCommand extends ProcessReportCommand<SingleProcessMapDurationReportResult> {

  @Override
  protected SingleProcessMapDurationReportResult filterResultData(final CommandContext<SingleProcessReportDefinitionDto> commandContext,
                                                                  final SingleProcessMapDurationReportResult evaluationResult) {
    return LatestFlowNodesOnlyFilter.filterResultData(commandContext, evaluationResult);
  }

}
