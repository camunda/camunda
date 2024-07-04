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
// import static io.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
//
// import com.google.common.collect.ImmutableMap;
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
// import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
// import io.camunda.optimize.test.util.decision.DmnHelper;
// import java.util.ArrayList;
// import java.util.Collections;
// import java.util.List;
// import java.util.Map;
// import java.util.Objects;
// import org.apache.commons.lang3.RandomStringUtils;
// import org.camunda.bpm.model.bpmn.BpmnModelInstance;
//
// public abstract class AbstractVariableIT extends AbstractPlatformIT {
//
//   protected static final String PROCESS_DEFINITION_KEY = "aProcessDefinitionKey";
//
//   protected String deployAndStartMultiTenantUserTaskProcess(
//       final String variableName, final List<String> deployedTenants) {
//     deployedTenants.stream()
//         .filter(Objects::nonNull)
//         .forEach(tenantId -> engineIntegrationExtension.createTenant(tenantId));
//     deployedTenants.forEach(
//         tenant -> {
//           final ProcessDefinitionEngineDto processDefinitionEngineDto =
//               deploySimpleProcessDefinition(tenant);
//           String randomValue = RandomStringUtils.random(10);
//           engineIntegrationExtension.startProcessInstance(
//               processDefinitionEngineDto.getId(), ImmutableMap.of(variableName, randomValue));
//         });
//     return PROCESS_DEFINITION_KEY;
//   }
//
//   protected String createSingleReport(final ProcessDefinitionEngineDto processDefinition) {
//     final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
//         reportClient.createSingleProcessReportDefinitionDto(
//             null, processDefinition.getKey(), new ArrayList<>(Collections.singletonList(null)));
//     singleProcessReportDefinitionDto
//         .getData()
//         .setProcessDefinitionVersion(processDefinition.getVersionAsString());
//     return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
//   }
//
//   protected ProcessDefinitionEngineDto deploySimpleProcessDefinition() {
//     return deploySimpleProcessDefinition(null);
//   }
//
//   protected void startInstanceAndImportEngineEntities(
//       final ProcessDefinitionEngineDto processDefinition, final Map<String, Object> variables) {
//     engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
//     importAllEngineEntitiesFromScratch();
//   }
//
//   protected DecisionDefinitionEngineDto startDecisionInstanceAndImportEngineEntities(
//       final Map<String, Object> variables) {
//     final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
//         engineIntegrationExtension.deployDecisionDefinition(
//             DmnHelper.createSimpleDmnModel("someKey"));
//     engineIntegrationExtension.startDecisionInstance(
//         decisionDefinitionEngineDto.getId(), variables);
//     importAllEngineEntitiesFromScratch();
//     return decisionDefinitionEngineDto;
//   }
//
//   protected ProcessDefinitionEngineDto deploySimpleProcessDefinition(final String tenantId) {
//     return deploySimpleProcessDefinition(PROCESS_DEFINITION_KEY, tenantId);
//   }
//
//   protected ProcessDefinitionEngineDto deploySimpleProcessDefinition(
//       final String key, final String tenantId) {
//     BpmnModelInstance modelInstance = getSimpleBpmnDiagram(key);
//     return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance,
// tenantId);
//   }
// }
