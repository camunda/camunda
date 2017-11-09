package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.query.report.result.ReportResultDto;
import org.camunda.optimize.service.exceptions.OptimizeException;

import java.io.IOException;

public interface Command {
  
  ReportResultDto evaluate(CommandContext commandContext) throws IOException, OptimizeException;
}
