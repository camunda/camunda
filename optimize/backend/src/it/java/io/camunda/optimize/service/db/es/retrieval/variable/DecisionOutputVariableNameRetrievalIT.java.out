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
// public class DecisionOutputVariableNameRetrievalIT extends DecisionVariableNameRetrievalIT {
//   @Override
//   protected DecisionDefinitionEngineDto deployDecisionsWithVarNames(
//       final List<String> varNames, List<DecisionTypeRef> types) {
//     if (varNames.size() > types.size()) {
//       types = nCopies(varNames.size(), STRING);
//     }
//     DmnModelGenerator.DecisionGenerator decisionGenerator =
// DmnModelGenerator.create().decision();
//     decisionGenerator.decisionDefinitionKey(DECISION_KEY);
//     decisionGenerator = decisionGenerator.addInput("input", STRING);
//     for (int i = 0; i < varNames.size(); i++) {
//       String varId = types.get(i) + varNames.get(i);
//       decisionGenerator = decisionGenerator.addOutput(varNames.get(i), varId, varId,
// types.get(i));
//     }
//     return engineIntegrationExtension.deployDecisionDefinition(
//         decisionGenerator.buildDecision().build());
//   }
//
//   @Override
//   protected List<DecisionVariableNameResponseDto> getVariableNames(
//       final DecisionVariableNameRequestDto variableRequestDto) {
//     return variablesClient.getDecisionOutputVariableNames(variableRequestDto);
//   }
//
//   @Override
//   protected List<DecisionVariableNameResponseDto> getVariableNames(
//       final List<DecisionDefinitionEngineDto> decisionDefinitions) {
//     return variablesClient.getDecisionOutputVariableNames(
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
//     return variablesClient.getDecisionOutputVariableNames(
//         new DecisionVariableNameRequestDto(key, versions));
//   }
// }
