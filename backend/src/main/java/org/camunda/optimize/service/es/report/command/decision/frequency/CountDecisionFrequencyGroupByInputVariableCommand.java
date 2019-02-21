package org.camunda.optimize.service.es.report.command.decision.frequency;

import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.INPUTS;

public class CountDecisionFrequencyGroupByInputVariableCommand
  extends CountDecisionFrequencyGroupByVariableCommand {

  public CountDecisionFrequencyGroupByInputVariableCommand() {
    super(INPUTS);
  }
}
