/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.importing.user_task;
//
// import static io.camunda.optimize.service.util.mapper.ObjectMapperFactory.OPTIMIZE_MAPPER;
// import static io.camunda.optimize.util.BpmnModels.getDoubleUserTaskDiagram;
// import static io.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;
//
// import com.fasterxml.jackson.databind.ObjectMapper;
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
// import io.camunda.optimize.exception.OptimizeIntegrationTestException;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import java.sql.SQLException;
// import java.time.OffsetDateTime;
// import java.time.temporal.ChronoUnit;
// import org.junit.jupiter.api.BeforeEach;
//
// public abstract class AbstractUserTaskImportIT extends AbstractPlatformIT {
//
//   protected ObjectMapper objectMapper;
//
//   @BeforeEach
//   public void setUp() {
//     if (objectMapper == null) {
//       objectMapper = OPTIMIZE_MAPPER;
//     }
//   }
//
//   protected void changeUserTaskIdleDuration(
//       final ProcessInstanceEngineDto processInstanceDto, final long idleDuration) {
//     engineIntegrationExtension
//         .getHistoricTaskInstances(processInstanceDto.getId())
//         .forEach(
//             historicUserTaskInstanceDto -> {
//               try {
//                 engineDatabaseExtension.changeUserTaskAssigneeClaimOperationTimestamp(
//                     historicUserTaskInstanceDto.getId(),
//                     historicUserTaskInstanceDto
//                         .getStartTime()
//                         .plus(idleDuration, ChronoUnit.MILLIS));
//               } catch (SQLException e) {
//                 throw new OptimizeIntegrationTestException(e);
//               }
//             });
//   }
//
//   protected void changeUserTaskWorkDuration(
//       final ProcessInstanceEngineDto processInstanceDto, final long workDuration) {
//     engineIntegrationExtension
//         .getHistoricTaskInstances(processInstanceDto.getId())
//         .forEach(
//             historicUserTaskInstanceDto -> {
//               if (historicUserTaskInstanceDto.getEndTime() != null) {
//                 try {
//                   engineDatabaseExtension.changeUserTaskAssigneeClaimOperationTimestamp(
//                       historicUserTaskInstanceDto.getId(),
//                       historicUserTaskInstanceDto
//                           .getEndTime()
//                           .minus(workDuration, ChronoUnit.MILLIS));
//                 } catch (SQLException e) {
//                   throw new OptimizeIntegrationTestException(e);
//                 }
//               }
//             });
//   }
//
//   protected void changeUnclaimTimestampForAssigneeId(
//       final ProcessInstanceEngineDto processInstanceDto,
//       final OffsetDateTime timestamp,
//       final String assigneeId) {
//     engineIntegrationExtension
//         .getHistoricTaskInstances(processInstanceDto.getId())
//         .forEach(
//             historicUserTaskInstanceDto -> {
//               try {
//                 engineDatabaseExtension
//                     .changeUserTaskAssigneeDeleteOperationWithAssigneeIdTimestamp(
//                         historicUserTaskInstanceDto.getId(), timestamp, assigneeId);
//               } catch (SQLException e) {
//                 throw new OptimizeIntegrationTestException(e);
//               }
//             });
//   }
//
//   protected void changeClaimTimestampForAssigneeId(
//       final ProcessInstanceEngineDto processInstanceDto,
//       final OffsetDateTime timestamp,
//       final String assigneeId) {
//     engineIntegrationExtension
//         .getHistoricTaskInstances(processInstanceDto.getId())
//         .forEach(
//             historicUserTaskInstanceDto -> {
//               try {
//
// engineDatabaseExtension.changeUserTaskAssigneeAddOperationWithAssigneeIdTimestamp(
//                     historicUserTaskInstanceDto.getId(), timestamp, assigneeId);
//               } catch (SQLException e) {
//                 throw new OptimizeIntegrationTestException(e);
//               }
//             });
//   }
//
//   protected void changeUserTaskEndTime(
//       final ProcessInstanceEngineDto processInstanceDto, final OffsetDateTime endTime) {
//     engineIntegrationExtension
//         .getHistoricTaskInstances(processInstanceDto.getId())
//         .forEach(
//             historicUserTaskInstanceDto -> {
//               engineDatabaseExtension.changeFlowNodeEndDate(
//                   processInstanceDto.getId(),
//                   historicUserTaskInstanceDto.getTaskDefinitionKey(),
//                   endTime);
//             });
//   }
//
//   protected void changeUserTaskStartTime(
//       final ProcessInstanceEngineDto processInstanceDto, final OffsetDateTime startTime) {
//     engineIntegrationExtension
//         .getHistoricTaskInstances(processInstanceDto.getId())
//         .forEach(
//             historicUserTaskInstanceDto -> {
//               engineDatabaseExtension.changeFlowNodeStartDate(
//                   processInstanceDto.getId(),
//                   historicUserTaskInstanceDto.getTaskDefinitionKey(),
//                   startTime);
//             });
//   }
//
//   protected ProcessInstanceEngineDto deployAndStartTwoUserTasksProcess() {
//     return engineIntegrationExtension.deployAndStartProcess(getDoubleUserTaskDiagram());
//   }
//
//   protected ProcessDefinitionEngineDto deployOneUserTaskDefinition() {
//     return engineIntegrationExtension.deployProcessAndGetProcessDefinition(
//         getSingleUserTaskDiagram());
//   }
// }
