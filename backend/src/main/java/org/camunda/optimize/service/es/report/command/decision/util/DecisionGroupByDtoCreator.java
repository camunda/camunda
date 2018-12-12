package org.camunda.optimize.service.es.report.command.decision.util;

import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByNoneDto;


public class DecisionGroupByDtoCreator {

  public static DecisionGroupByDto createGroupDecisionByNone() {
    return new DecisionGroupByNoneDto();
  }

}
