/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.db.es.retrieval.variable;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.test.util.decision.DecisionTypeRef.STRING;
// import static java.util.Collections.nCopies;
//
// import io.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameRequestDto;
// import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
// import io.camunda.optimize.test.util.decision.DecisionTypeRef;
// import io.camunda.optimize.test.util.decision.DmnModelGenerator;
// import java.util.List;
// import java.util.stream.Collectors;
// import org.junit.jupiter.api.Tag;
//
// @Tag(OPENSEARCH_PASSING)
// public class DecisionInputVariableNameRetrievalIT extends DecisionVariableNameRetrievalIT {
//   @Override
//   protected DecisionDefinitionEngineDto deployDecisionsWithVarNames(
//       final List<String> varNames, List<DecisionTypeRef> types) {
//     if (varNames.size() > types.size()) {
//       types = nCopies(varNames.size(), STRING);
//     }
//     DmnModelGenerator.DecisionGenerator decisionGenerator =
// DmnModelGenerator.create().decision();
//     decisionGenerator.decisionDefinitionKey(DECISION_KEY);
//     for (int i = 0; i < varNames.size(); i++) {
//       String varId = types.get(i) + varNames.get(i);
//       decisionGenerator = decisionGenerator.addInput(varNames.get(i), varId, varId,
// types.get(i));
//     }
//     decisionGenerator = decisionGenerator.addOutput("output", STRING);
//     return engineIntegrationExtension.deployDecisionDefinition(
//         decisionGenerator.buildDecision().build());
//   }
//
//   @Override
//   protected List<DecisionVariableNameResponseDto> getVariableNames(
//       final DecisionVariableNameRequestDto variableRequestDto) {
//     return variablesClient.getDecisionInputVariableNames(variableRequestDto);
//   }
//
//   @Override
//   protected List<DecisionVariableNameResponseDto> getVariableNames(
//       final List<DecisionDefinitionEngineDto> decisionDefinitions) {
//     return variablesClient.getDecisionInputVariableNames(
//         decisionDefinitions.stream()
//             .map(
//                 definition ->
//                     new DecisionVariableNameRequestDto(
//                         definition.getKey(),
//                         definition.getVersionAsString(),
//                         definition.getTenantId().orElse(null)))
//             .collect(Collectors.toList()));
//   }
//
//   @Override
//   protected List<DecisionVariableNameResponseDto> getVariableNames(
//       final String key, final List<String> versions) {
//     return variablesClient.getDecisionInputVariableNames(
//         new DecisionVariableNameRequestDto(key, versions));
//   }
// }
