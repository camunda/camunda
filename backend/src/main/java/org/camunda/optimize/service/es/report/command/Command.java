package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.camunda.optimize.service.exceptions.OptimizeException;

public interface Command<RD extends ReportDefinitionDto> {
  
  ReportEvaluationResult evaluate(CommandContext<RD> commandContext) throws OptimizeException;

}
