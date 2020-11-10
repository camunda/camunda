/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.retrieval.variable;

import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import org.camunda.optimize.test.util.decision.DecisionTypeRef;
import org.camunda.optimize.test.util.decision.DmnModelGenerator;

import java.util.List;

import static java.util.Collections.nCopies;
import static org.camunda.optimize.test.util.decision.DecisionTypeRef.STRING;

public class DecisionInputVariableNameRetrievalIT extends DecisionVariableNameRetrievalIT {

  protected DecisionDefinitionEngineDto deployDecisionsWithVarNames(List<String> varNames,
                                                                    List<DecisionTypeRef> types) {
    if (varNames.size() > types.size()) {
      types = nCopies(varNames.size(), STRING);
    }
    DmnModelGenerator.DecisionGenerator decisionGenerator = DmnModelGenerator.create().decision();
    decisionGenerator.decisionDefinitionKey(DECISION_KEY);
    for (int i = 0; i < varNames.size(); i++) {
      decisionGenerator = decisionGenerator.addInput(varNames.get(i), types.get(i));
    }
    decisionGenerator = decisionGenerator.addOutput("output", STRING);
    return engineIntegrationExtension.deployDecisionDefinition(decisionGenerator.buildDecision().build());
  }

  protected List<DecisionVariableNameResponseDto> getVariableNames(DecisionVariableNameRequestDto variableRequestDto) {
    return variablesClient.getDecisionInputVariableNames(variableRequestDto);
  }

  protected List<DecisionVariableNameResponseDto> getVariableNames(DecisionDefinitionEngineDto decisionDefinition) {
    return variablesClient.getDecisionInputVariableNames(decisionDefinition);
  }

  protected List<DecisionVariableNameResponseDto> getVariableNames(String key, List<String> versions) {
    return variablesClient.getDecisionInputVariableNames(key, versions);
  }

}
