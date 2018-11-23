package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.query.report.single.result.SingleReportResultDto;
import org.camunda.optimize.service.exceptions.OptimizeException;

public interface Command {
  
  SingleReportResultDto evaluate(CommandContext commandContext) throws OptimizeException;
}
