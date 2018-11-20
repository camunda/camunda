package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.service.exceptions.OptimizeException;

public interface Command {
  
  ProcessReportResultDto evaluate(CommandContext commandContext) throws OptimizeException;
}
