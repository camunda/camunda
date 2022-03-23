/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.retrieval.variable;

import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import org.camunda.optimize.test.util.decision.DecisionTypeRef;
import org.camunda.optimize.test.util.decision.DmnModelGenerator;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.nCopies;
import static org.camunda.optimize.test.util.decision.DecisionTypeRef.STRING;

public class DecisionInputVariableNameRetrievalIT extends DecisionVariableNameRetrievalIT {
  @Override
  protected DecisionDefinitionEngineDto deployDecisionsWithVarNames(final List<String> varNames,
                                                                    List<DecisionTypeRef> types) {
    if (varNames.size() > types.size()) {
      types = nCopies(varNames.size(), STRING);
    }
    DmnModelGenerator.DecisionGenerator decisionGenerator = DmnModelGenerator.create().decision();
    decisionGenerator.decisionDefinitionKey(DECISION_KEY);
    for (int i = 0; i < varNames.size(); i++) {
      String varId = types.get(i) + varNames.get(i);
      decisionGenerator = decisionGenerator.addInput(varNames.get(i), varId, varId, types.get(i));
    }
    decisionGenerator = decisionGenerator.addOutput("output", STRING);
    return engineIntegrationExtension.deployDecisionDefinition(decisionGenerator.buildDecision().build());
  }

  @Override
  protected List<DecisionVariableNameResponseDto> getVariableNames(final DecisionVariableNameRequestDto variableRequestDto) {
    return variablesClient.getDecisionInputVariableNames(variableRequestDto);
  }

  @Override
  protected List<DecisionVariableNameResponseDto> getVariableNames(final List<DecisionDefinitionEngineDto> decisionDefinitions) {
    return variablesClient.getDecisionInputVariableNames(
      decisionDefinitions.stream()
        .map(definition -> new DecisionVariableNameRequestDto(
          definition.getKey(), definition.getVersionAsString(), definition.getTenantId().orElse(null)
        ))
        .collect(Collectors.toList())
    );
  }

  @Override
  protected List<DecisionVariableNameResponseDto> getVariableNames(final String key, final List<String> versions) {
    return variablesClient.getDecisionInputVariableNames(new DecisionVariableNameRequestDto(key, versions));
  }

}
