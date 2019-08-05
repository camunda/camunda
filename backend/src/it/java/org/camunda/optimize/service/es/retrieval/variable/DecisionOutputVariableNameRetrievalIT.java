/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval.variable;

import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import org.camunda.optimize.test.util.decision.DecisionTypeRef;
import org.camunda.optimize.test.util.decision.DmnModelGenerator;

import java.util.List;

import static java.util.Collections.nCopies;
import static org.camunda.optimize.test.util.decision.DecisionTypeRef.STRING;

public class DecisionOutputVariableNameRetrievalIT extends DecisionVariableNameRetrievalIT {

  protected DecisionDefinitionEngineDto deployDecisionsWithVarNames(List<String> varNames,
                                                                    List<DecisionTypeRef> types) {
    if (varNames.size() > types.size()) {
      types = nCopies(varNames.size(), STRING);
    }
    DmnModelGenerator.DecisionGenerator decisionGenerator = DmnModelGenerator.create().decision();
    decisionGenerator.decisionDefinitionKey(DECISION_KEY);
    decisionGenerator = decisionGenerator.addInput("input", STRING);
    for (int i = 0; i < varNames.size(); i++) {
      decisionGenerator = decisionGenerator.addOutput(varNames.get(i), types.get(i));
    }
    return engineRule.deployDecisionDefinition(decisionGenerator.buildDecision().build());
  }

  protected List<DecisionVariableNameDto> getVariableNames(DecisionVariableNameRequestDto variableRequestDto) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildDecisionOutputVariableNamesRequest(variableRequestDto)
      .executeAndReturnList(DecisionVariableNameDto.class, 200);
  }

}
