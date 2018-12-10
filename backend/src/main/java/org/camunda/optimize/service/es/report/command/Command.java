package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.service.exceptions.OptimizeException;

public interface Command {
  
  ReportResultDto evaluate(CommandContext commandContext) throws OptimizeException;
}
