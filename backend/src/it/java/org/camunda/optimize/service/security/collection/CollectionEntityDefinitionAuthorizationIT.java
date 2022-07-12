/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.collection;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionRestDto;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public class CollectionEntityDefinitionAuthorizationIT extends AbstractCollectionRoleIT {

  @Test
  public void userOnlyGetsReportsEntitiesOfAuthorizedDefinitions() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    ProcessDefinitionEngineDto authorizedProcessDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      BpmnModels.getSingleServiceTaskProcess("authorized"));
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      KERMIT_USER, authorizedProcessDefinition.getKey(), RESOURCE_TYPE_PROCESS_DEFINITION
    );
    ProcessDefinitionEngineDto unauthorizedProcessDefinition =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(
        BpmnModels.getSingleServiceTaskProcess("unauthorized"));

    importAllEngineEntitiesFromScratch();

    final String collectionId = collectionClient.createNewCollectionWithProcessScope(authorizedProcessDefinition);
    collectionClient.createScopeForCollection(collectionId, "unauthorized", DefinitionType.PROCESS);
    addRoleToCollectionAsDefaultUser(RoleType.VIEWER, new IdentityDto(KERMIT_USER, IdentityType.USER), collectionId);

    String expectedReport = createSingleProcessReportForDefinitionAsDefaultUser(
      authorizedProcessDefinition,
      collectionId
    );
    createSingleProcessReportForDefinitionAsDefaultUser(unauthorizedProcessDefinition, collectionId);

    // when
    AuthorizedCollectionDefinitionRestDto collection = collectionClient.getAuthorizedCollectionById(
      collectionId,
      KERMIT_USER,
      KERMIT_USER
    );

    final List<EntityResponseDto> entities = collectionClient.getEntitiesForCollection(
      collectionId,
      KERMIT_USER,
      KERMIT_USER
    );

    // then
    assertThat(collection.getDefinitionDto().getId()).isEqualTo(collectionId);
    assertThat(entities.stream().map(EntityResponseDto::getId).collect(toList())).containsExactly(expectedReport);
  }

  private String createSingleProcessReportForDefinitionAsDefaultUser(final ProcessDefinitionEngineDto processDefinitionEngineDto,
                                                                     final String collectionId) {
    final ProcessReportDataDto reportDataDto = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionEngineDto.getKey())
      .setProcessDefinitionVersion(processDefinitionEngineDto.getVersionAsString())
      .setGroupByDateInterval(AggregateByDateUnit.AUTOMATIC)
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_END_DATE)
      .build();

    final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(reportDataDto);
    singleProcessReportDefinitionDto.setCollectionId(collectionId);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

}
