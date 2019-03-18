package org.camunda.optimize.service.es.report.command.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapDurationReportResult;

public abstract class FlowNodeDurationGroupingCommand extends ProcessReportCommand<SingleProcessMapDurationReportResult> {

  @Override
  protected SingleProcessMapDurationReportResult filterResultData(final CommandContext<ProcessReportDataDto> commandContext,
                                                                  final SingleProcessMapDurationReportResult evaluationResult) {
    return LatestFlowNodesOnlyFilter.filterResultData(commandContext, evaluationResult);
  }

}
