/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.importing.permutations;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
// import static org.assertj.core.api.Assertions.assertThat;
// import static org.assertj.core.api.Assertions.tuple;
//
// import com.google.common.collect.ImmutableList;
// import io.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
// import io.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.importing.ImportMediator;
// import
// io.camunda.optimize.service.importing.engine.mediator.RunningActivityInstanceEngineImportMediator;
// import
// io.camunda.optimize.service.importing.engine.mediator.RunningProcessInstanceEngineImportMediator;
// import
// io.camunda.optimize.service.importing.engine.mediator.RunningUserTaskInstanceEngineImportMediator;
// import
// io.camunda.optimize.service.importing.engine.mediator.UserOperationLogEngineImportMediator;
// import io.camunda.optimize.service.importing.engine.mediator.VariableUpdateEngineImportMediator;
// import java.util.List;
// import java.util.stream.Stream;
// import org.junit.jupiter.api.BeforeAll;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.MethodSource;
//
// @Tag(OPENSEARCH_PASSING)
// public class RunningSuspendedProcessInstanceMediatorPermutationsImportIT
//     extends AbstractImportMediatorPermutationsIT {
//
//   @BeforeAll
//   public static void given() {
//     // given
//     final ProcessInstanceEngineDto processInstanceDto = deployAndStartUserTaskProcess();
//     engineIntegrationExtension.suspendProcessInstanceByInstanceId(processInstanceDto.getId());
//   }
//
//   @ParameterizedTest(
//       name =
//           "Running Activities of a suspended process instance are fully imported with mediator
// order {0}")
//   @MethodSource("runningActivityRelatedMediators")
//   public void runningSuspendedInstanceIsFullyImported(
//       final List<Class<? extends ImportMediator>> mediatorOrder) {
//     // when
//     performOrderedImport(mediatorOrder);
//     databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//
//     // then
//     assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
//         .hasSize(1)
//         .singleElement()
//         .satisfies(
//             persistedProcessInstanceDto -> {
//               // general instance sanity check
//               assertThat(persistedProcessInstanceDto.getStartDate()).isNotNull();
//               assertThat(persistedProcessInstanceDto.getEndDate()).isNull();
//               assertThat(persistedProcessInstanceDto.getState()).isEqualTo(SUSPENDED_STATE);
//               assertThat(persistedProcessInstanceDto.getFlowNodeInstances())
//                   // only the running activity is imported
//                   .hasSize(1)
//                   .allSatisfy(activity -> assertThat(activity.getStartDate()).isNotNull())
//                   .extracting(
//                       FlowNodeInstanceDto::getEndDate, FlowNodeInstanceDto::getTotalDurationInMs)
//                   .singleElement()
//                   .isEqualTo(tuple(null, null));
//               assertThat(persistedProcessInstanceDto.getUserTasks())
//                   .hasSize(1)
//                   .singleElement()
//                   .satisfies(
//                       userTask -> {
//                         assertThat(userTask.getStartDate()).isNotNull();
//                         assertThat(userTask.getEndDate()).isNull();
//                         assertThat(userTask.getIdleDurationInMs()).isNull();
//                         assertThat(userTask.getWorkDurationInMs()).isNull();
//                       });
//             });
//
//     final List<CamundaActivityEventDto> allStoredCamundaActivityEventsForDefinition =
//         databaseIntegrationTestExtension.getAllStoredCamundaActivityEventsForDefinition(
//             TEST_PROCESS);
//     // the process instance start and the single running user task event
//     assertThat(allStoredCamundaActivityEventsForDefinition).hasSize(2);
//   }
//
//   private static Stream<List<Class<? extends ImportMediator>>> runningActivityRelatedMediators()
// {
//     return getMediatorPermutationsStream(
//         ImmutableList.of(
//             RunningActivityInstanceEngineImportMediator.class,
//             RunningUserTaskInstanceEngineImportMediator.class,
//             RunningProcessInstanceEngineImportMediator.class,
//             VariableUpdateEngineImportMediator.class,
//             UserOperationLogEngineImportMediator.class));
//   }
// }
