package org.camunda.optimize.service.es.report.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotSupportedCommand extends ReportCommand {
  private static final Logger logger = LoggerFactory.getLogger(NotSupportedCommand.class);

  @Override
  protected ReportEvaluationResult evaluate() {
    // Error should contain the report Name
    try {
      logger.warn("The following settings combination of the report data is not supported in Optimize: \n" +
                    "{} \n " +
                    "Therefore returning error result.", objectMapper.writeValueAsString(reportDefinition));
    } catch (JsonProcessingException e) {
      logger.error("can't serialize report data", e);
    }
    throw new OptimizeValidationException("This combination of the settings of the report builder is not supported!");
  }

  @Override
  protected void sortResultData(final ReportEvaluationResult evaluationResult) {
    // noop
  }

  @Override
  protected void beforeEvaluate(final CommandContext commandContext) {
    // noop
  }
}
