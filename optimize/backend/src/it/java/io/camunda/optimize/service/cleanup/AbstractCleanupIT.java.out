/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.cleanup;
//
// import static io.camunda.optimize.service.db.DatabaseConstants.BUSINESS_KEY_INDEX_NAME;
// import static
// io.camunda.optimize.service.db.DatabaseConstants.CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import com.google.common.collect.Lists;
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
// import io.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
// import io.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.security.util.LocalDateUtil;
// import io.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
// import io.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
// import io.camunda.optimize.service.util.configuration.cleanup.ProcessCleanupConfiguration;
// import
// io.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
// import io.camunda.optimize.test.util.VariableTestUtil;
// import java.io.IOException;
// import java.time.Instant;
// import java.time.OffsetDateTime;
// import java.util.Collections;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.stream.Collectors;
// import lombok.SneakyThrows;
// import org.apache.commons.text.RandomStringGenerator;
// import org.camunda.bpm.model.bpmn.Bpmn;
// import org.camunda.bpm.model.bpmn.BpmnModelInstance;
//
// public abstract class AbstractCleanupIT extends AbstractPlatformIT {
//   private static final RandomStringGenerator KEY_GENERATOR =
//       new RandomStringGenerator.Builder().withinRange('a', 'z').build();
//
//   protected void cleanUpEventIndices() {
//     databaseIntegrationTestExtension.deleteAllExternalEventIndices();
//     databaseIntegrationTestExtension.deleteAllVariableUpdateInstanceIndices();
//     embeddedOptimizeExtension
//         .getDatabaseSchemaManager()
//         .createOrUpdateOptimizeIndex(
//             embeddedOptimizeExtension.getOptimizeDatabaseClient(),
//             databaseIntegrationTestExtension.getEventIndex());
//     embeddedOptimizeExtension
//         .getDatabaseSchemaManager()
//         .createOrUpdateOptimizeIndex(
//             embeddedOptimizeExtension.getOptimizeDatabaseClient(),
//             databaseIntegrationTestExtension.getVariableUpdateInstanceIndex());
//   }
//
//   protected void configureHigherProcessSpecificTtl(final String processDefinitionKey) {
//     getCleanupConfiguration()
//         .getProcessDataCleanupConfiguration()
//         .getProcessDefinitionSpecificConfiguration()
//         .put(
//             processDefinitionKey,
//             ProcessDefinitionCleanupConfiguration.builder()
//                 .cleanupMode(CleanupMode.ALL)
//                 // higher ttl than default
//                 .ttl(getCleanupConfiguration().getTtl().plusYears(5L))
//                 .build());
//   }
//
//   @SneakyThrows
//   protected void assertNoProcessInstanceDataExists(final List<ProcessInstanceEngineDto>
// instances) {
//     assertThat(getProcessInstancesById(extractProcessInstanceIds(instances)).isEmpty());
//   }
//
//   protected ProcessInstanceEngineDto startNewInstanceWithEndTimeLessThanTtl(
//       final ProcessInstanceEngineDto originalInstance) {
//     return startNewInstanceWithEndTime(getEndTimeLessThanGlobalTtl(), originalInstance);
//   }
//
//   protected ProcessInstanceEngineDto startNewInstanceWithEndTime(
//       final OffsetDateTime endTime, final ProcessInstanceEngineDto originalInstance) {
//     final ProcessInstanceEngineDto processInstance =
//         startNewProcessWithSameProcessDefinitionId(originalInstance);
//     modifyProcessInstanceEndTime(endTime, processInstance);
//     return processInstance;
//   }
//
//   protected List<String> extractProcessInstanceIds(
//       final List<ProcessInstanceEngineDto> unaffectedProcessInstances) {
//     return unaffectedProcessInstances.stream()
//         .map(ProcessInstanceEngineDto::getId)
//         .collect(Collectors.toList());
//   }
//
//   protected ProcessInstanceEngineDto startNewProcessWithSameProcessDefinitionId(
//       ProcessInstanceEngineDto processInstance) {
//     return engineIntegrationExtension.startProcessInstance(
//         processInstance.getDefinitionId(), VariableTestUtil.createAllPrimitiveTypeVariables());
//   }
//
//   protected OffsetDateTime getEndTimeLessThanGlobalTtl() {
//     return LocalDateUtil.getCurrentDateTime()
//         .minus(getCleanupConfiguration().getTtl())
//         .minusSeconds(1);
//   }
//
//   @SneakyThrows
//   protected List<ProcessInstanceEngineDto>
//       deployProcessAndStartTwoProcessInstancesWithEndTimeLessThanTtl() {
//     return deployProcessAndStartTwoProcessInstancesWithEndTime(getEndTimeLessThanGlobalTtl());
//   }
//
//   @SneakyThrows
//   protected List<ProcessInstanceEngineDto> deployProcessAndStartTwoProcessInstancesWithEndTime(
//       OffsetDateTime endTime) {
//     final ProcessInstanceEngineDto firstProcInst = deployAndStartSimpleServiceTask();
//     final ProcessInstanceEngineDto secondProcInst =
//         startNewProcessWithSameProcessDefinitionId(firstProcInst);
//     secondProcInst.setProcessDefinitionKey(firstProcInst.getProcessDefinitionKey());
//
//     modifyProcessInstanceEndTime(endTime, firstProcInst, secondProcInst);
//
//     return Lists.newArrayList(firstProcInst, secondProcInst);
//   }
//
//   @SneakyThrows
//   protected void modifyProcessInstanceEndTime(
//       final OffsetDateTime endTime, final ProcessInstanceEngineDto... processInstances) {
//     Map<String, OffsetDateTime> procInstEndDateUpdates = new HashMap<>();
//     for (final ProcessInstanceEngineDto instance : processInstances) {
//       procInstEndDateUpdates.put(instance.getId(), endTime);
//     }
//     engineDatabaseExtension.changeProcessInstanceEndDates(procInstEndDateUpdates);
//   }
//
//   @SneakyThrows
//   protected void assertVariablesEmptyInProcessInstances(final List<String> instanceIds) {
//     assertThat(getProcessInstancesById(instanceIds))
//         .hasSameSizeAs(instanceIds)
//         .allMatch(processInstanceDto -> processInstanceDto.getVariables().isEmpty());
//   }
//
//   protected List<ProcessInstanceDto> getProcessInstancesById(final List<String> instanceIds)
//       throws IOException {
//     return databaseIntegrationTestExtension.getProcessInstancesById(instanceIds);
//   }
//
//   protected void assertPersistedProcessInstanceDataComplete(final String instanceId)
//       throws IOException {
//     assertPersistedProcessInstanceDataComplete(Collections.singletonList(instanceId));
//   }
//
//   protected void assertPersistedProcessInstanceDataComplete(final List<String> instanceIds)
//       throws IOException {
//     List<ProcessInstanceDto> idsResp = getProcessInstancesById(instanceIds);
//     assertThat(idsResp).hasSameSizeAs(instanceIds);
//     assertThat(idsResp)
//         .allSatisfy(
//             processInstanceDto -> assertThat(processInstanceDto.getVariables()).isNotEmpty());
//   }
//
//   protected ProcessInstanceEngineDto deployAndStartSimpleServiceTask() {
//     BpmnModelInstance processModel =
//         Bpmn.createExecutableProcess(KEY_GENERATOR.generate(8))
//             .name("aProcessName")
//             .startEvent()
//             .serviceTask()
//             .camundaExpression("${true}")
//             .endEvent()
//             .done();
//     return engineIntegrationExtension.deployAndStartProcessWithVariables(
//         processModel, VariableTestUtil.createAllPrimitiveTypeVariables());
//   }
//
//   protected Instant getTimestampLessThanIngestedEventsTtl() {
//     return OffsetDateTime.now()
//         .minus(getCleanupConfiguration().getTtl())
//         .minusSeconds(1)
//         .toInstant();
//   }
//
//   protected ProcessCleanupConfiguration getProcessDataCleanupConfiguration() {
//     return getCleanupConfiguration().getProcessDataCleanupConfiguration();
//   }
//
//   protected CleanupConfiguration getCleanupConfiguration() {
//     return embeddedOptimizeExtension.getConfigurationService().getCleanupServiceConfiguration();
//   }
//
//   protected List<CamundaActivityEventDto> getCamundaActivityEvents() {
//     return databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
//         CAMUNDA_ACTIVITY_EVENT_INDEX_PREFIX + "*", CamundaActivityEventDto.class);
//   }
//
//   protected List<BusinessKeyDto> getAllCamundaEventBusinessKeys() {
//     return databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
//         BUSINESS_KEY_INDEX_NAME, BusinessKeyDto.class);
//   }
// }
