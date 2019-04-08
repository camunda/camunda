/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.decision.frequency;

import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.INPUTS;

public class CountDecisionFrequencyGroupByInputVariableCommand
  extends CountDecisionFrequencyGroupByVariableCommand {

  public CountDecisionFrequencyGroupByInputVariableCommand() {
    super(INPUTS);
  }
}
