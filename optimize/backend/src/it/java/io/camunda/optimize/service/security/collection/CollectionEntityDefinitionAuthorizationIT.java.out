/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.security.collection;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static
// io.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
// import static io.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
// import static java.util.stream.Collectors.toList;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.DefinitionType;
// import io.camunda.optimize.dto.optimize.IdentityDto;
// import io.camunda.optimize.dto.optimize.IdentityType;
// import io.camunda.optimize.dto.optimize.RoleType;
// import io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
// import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
// import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
// import io.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionRestDto;
// import io.camunda.optimize.service.util.ProcessReportDataType;
// import io.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
// import io.camunda.optimize.util.BpmnModels;
// import java.util.List;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class CollectionEntityDefinitionAuthorizationIT extends AbstractCollectionRoleIT {
//
//   @Test
//   public void userOnlyGetsReportsEntitiesOfAuthorizedDefinitions() {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//
//     ProcessDefinitionEngineDto authorizedProcessDefinition =
//         engineIntegrationExtension.deployProcessAndGetProcessDefinition(
//             BpmnModels.getSingleServiceTaskProcess("authorized"));
//     authorizationClient.grantSingleResourceAuthorizationsForUser(
//         KERMIT_USER, authorizedProcessDefinition.getKey(), RESOURCE_TYPE_PROCESS_DEFINITION);
//     ProcessDefinitionEngineDto unauthorizedProcessDefinition =
//         engineIntegrationExtension.deployProcessAndGetProcessDefinition(
//             BpmnModels.getSingleServiceTaskProcess("unauthorized"));
//
//     importAllEngineEntitiesFromScratch();
//
//     final String collectionId =
//         collectionClient.createNewCollectionWithProcessScope(authorizedProcessDefinition);
//     collectionClient.createScopeForCollection(collectionId, "unauthorized",
// DefinitionType.PROCESS);
//     addRoleToCollectionAsDefaultUser(
//         RoleType.VIEWER, new IdentityDto(KERMIT_USER, IdentityType.USER), collectionId);
//
//     String expectedReport =
//         createSingleProcessReportForDefinitionAsDefaultUser(
//             authorizedProcessDefinition, collectionId);
//     createSingleProcessReportForDefinitionAsDefaultUser(
//         unauthorizedProcessDefinition, collectionId);
//
//     // when
//     AuthorizedCollectionDefinitionRestDto collection =
//         collectionClient.getAuthorizedCollectionById(collectionId, KERMIT_USER, KERMIT_USER);
//
//     final List<EntityResponseDto> entities =
//         collectionClient.getEntitiesForCollection(collectionId, KERMIT_USER, KERMIT_USER);
//
//     // then
//     assertThat(collection.getDefinitionDto().getId()).isEqualTo(collectionId);
//     assertThat(entities.stream().map(EntityResponseDto::getId).collect(toList()))
//         .containsExactly(expectedReport);
//   }
//
//   private String createSingleProcessReportForDefinitionAsDefaultUser(
//       final ProcessDefinitionEngineDto processDefinitionEngineDto, final String collectionId) {
//     final ProcessReportDataDto reportDataDto =
//         TemplatedProcessReportDataBuilder.createReportData()
//             .setProcessDefinitionKey(processDefinitionEngineDto.getKey())
//             .setProcessDefinitionVersion(processDefinitionEngineDto.getVersionAsString())
//             .setGroupByDateInterval(AggregateByDateUnit.AUTOMATIC)
//             .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_END_DATE)
//             .build();
//
//     final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
//         new SingleProcessReportDefinitionRequestDto();
//     singleProcessReportDefinitionDto.setData(reportDataDto);
//     singleProcessReportDefinitionDto.setCollectionId(collectionId);
//     return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
//   }
// }
