package org.camunda.optimize.service.es.report.command.decision.frequency;

import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.OUTPUTS;

public class CountDecisionFrequencyGroupByOutputVariableCommand
  extends CountDecisionFrequencyGroupByVariableCommand {

  public CountDecisionFrequencyGroupByOutputVariableCommand() {
    super(OUTPUTS);
  }
}
