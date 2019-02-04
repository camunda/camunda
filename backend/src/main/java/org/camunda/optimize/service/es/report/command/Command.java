package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.service.es.report.result.ReportResult;
import org.camunda.optimize.service.exceptions.OptimizeException;

public interface Command {
  
  ReportResult evaluate(CommandContext commandContext) throws OptimizeException;

}
