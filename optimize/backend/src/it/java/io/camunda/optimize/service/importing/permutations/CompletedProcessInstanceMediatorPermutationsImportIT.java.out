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
// import static io.camunda.optimize.dto.optimize.ProcessInstanceConstants.COMPLETED_STATE;
// import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import com.google.common.collect.ImmutableList;
// import io.camunda.optimize.dto.optimize.query.event.process.CamundaActivityEventDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import io.camunda.optimize.service.importing.ImportMediator;
// import
// io.camunda.optimize.service.importing.engine.mediator.CompletedActivityInstanceEngineImportMediator;
// import
// io.camunda.optimize.service.importing.engine.mediator.CompletedProcessInstanceEngineImportMediator;
// import
// io.camunda.optimize.service.importing.engine.mediator.CompletedUserTaskEngineImportMediator;
// import io.camunda.optimize.service.importing.engine.mediator.IdentityLinkLogEngineImportMediator;
// import io.camunda.optimize.service.importing.engine.mediator.VariableUpdateEngineImportMediator;
// import java.util.List;
// import java.util.stream.Stream;
// import org.junit.jupiter.api.BeforeAll;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.MethodSource;
//
// @Tag(OPENSEARCH_PASSING)
// public class CompletedProcessInstanceMediatorPermutationsImportIT
//     extends AbstractImportMediatorPermutationsIT {
//
//   @BeforeAll
//   public static void given() {
//     // given
//     final ProcessInstanceEngineDto processInstanceDto = deployAndStartUserTaskProcess();
//     engineIntegrationExtension.claimAllRunningUserTasks(processInstanceDto.getId());
//     engineIntegrationExtension.addCandidateGroupForAllRunningUserTasks(
//         processInstanceDto.getId(), CANDIDATE_GROUP);
//     engineIntegrationExtension.finishAllRunningUserTasks(processInstanceDto.getId());
//   }
//
//   @ParameterizedTest(
//       name = "Completed Process Instances are fully imported with mediator order {0}")
//   @MethodSource("completedActivityRelatedMediators")
//   public void completedInstanceIsFullyImportedCamundaEventImportEnabled(
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
//               assertThat(persistedProcessInstanceDto.getEndDate()).isNotNull();
//               assertThat(persistedProcessInstanceDto.getState()).isEqualTo(COMPLETED_STATE);
//               assertThat(persistedProcessInstanceDto.getVariables())
//                   .hasSize(2)
//                   .allSatisfy(
//                       variable -> {
//                         assertThat(variable.getName()).isNotNull();
//                         assertThat(variable.getValue()).isNotNull();
//                         assertThat(variable.getType()).isNotNull();
//                         assertThat(variable.getVersion()).isEqualTo(1L);
//                       });
//               assertThat(persistedProcessInstanceDto.getFlowNodeInstances())
//                   .hasSize(3)
//                   .allSatisfy(
//                       activity -> {
//                         assertThat(activity.getStartDate()).isNotNull();
//                         assertThat(activity.getEndDate()).isNotNull();
//                         assertThat(activity.getTotalDurationInMs()).isGreaterThanOrEqualTo(0L);
//                       });
//               assertThat(persistedProcessInstanceDto.getUserTasks())
//                   .hasSize(1)
//                   .singleElement()
//                   .satisfies(
//                       userTask -> {
//                         assertThat(userTask.getStartDate()).isNotNull();
//                         assertThat(userTask.getEndDate()).isNotNull();
//                         assertThat(userTask.getAssignee()).isEqualTo(DEFAULT_USERNAME);
//                         assertThat(userTask.getCandidateGroups()).containsOnly(CANDIDATE_GROUP);
//                         assertThat(userTask.getIdleDurationInMs()).isGreaterThan(0L);
//                         assertThat(userTask.getWorkDurationInMs()).isGreaterThan(0L);
//                         assertThat(userTask.getAssigneeOperations()).hasSize(1);
//                       });
//             });
//
//     final List<CamundaActivityEventDto> allStoredCamundaActivityEventsForDefinition =
//         databaseIntegrationTestExtension.getAllStoredCamundaActivityEventsForDefinition(
//             TEST_PROCESS);
//     // start event, end event, user task start/end, process instance start/end
//     assertThat(allStoredCamundaActivityEventsForDefinition).hasSize(6);
//   }
//
//   private static Stream<List<Class<? extends ImportMediator>>>
// completedActivityRelatedMediators() {
//     return getMediatorPermutationsStream(
//         ImmutableList.of(
//             CompletedActivityInstanceEngineImportMediator.class,
//             CompletedUserTaskEngineImportMediator.class,
//             CompletedProcessInstanceEngineImportMediator.class,
//             IdentityLinkLogEngineImportMediator.class,
//             VariableUpdateEngineImportMediator.class));
//   }
// }
