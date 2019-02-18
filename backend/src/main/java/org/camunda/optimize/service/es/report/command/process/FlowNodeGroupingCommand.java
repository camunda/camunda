package org.camunda.optimize.service.es.report.command.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapReportResult;

public abstract class FlowNodeGroupingCommand extends ProcessReportCommand<SingleProcessMapReportResult> {

  @Override
  protected SingleProcessMapReportResult filterResultData(final CommandContext<ProcessReportDataDto> commandContext,
                                                          final SingleProcessMapReportResult evaluationResult) {
    return LatestFlowNodesOnlyFilter.filterResultData(commandContext, evaluationResult);
  }

}
