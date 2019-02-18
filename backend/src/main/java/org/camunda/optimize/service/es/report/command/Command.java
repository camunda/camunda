package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.service.es.report.result.ReportResult;
import org.camunda.optimize.service.exceptions.OptimizeException;

public interface Command<RD extends ReportDataDto> {
  
  ReportResult evaluate(CommandContext<RD> commandContext) throws OptimizeException;

}
