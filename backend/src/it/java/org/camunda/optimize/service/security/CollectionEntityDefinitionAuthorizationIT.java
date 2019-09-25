/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import junitparams.JUnitParamsRunner;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedResolvedCollectionDefinitionDto;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;

@RunWith(JUnitParamsRunner.class)
public class CollectionEntityDefinitionAuthorizationIT extends AbstractCollectionRoleIT {

  @Test
  public void userOnlyGetsReportsEntitiesOfAuthorizedDefinitions() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    ProcessDefinitionEngineDto authorizedProcessDefinition = deploySimpleServiceTaskProcess("authorized");
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      KERMIT_USER, authorizedProcessDefinition.getKey(), RESOURCE_TYPE_PROCESS_DEFINITION
    );
    ProcessDefinitionEngineDto unauthorizedProcessDefinition = deploySimpleServiceTaskProcess("unauthorized");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();

    final String collectionId = createNewCollectionAsDefaultUser();
    addRoleToCollectionAsDefaultUser(
      RoleType.VIEWER, new IdentityDto(KERMIT_USER, IdentityType.USER), collectionId
    );

    String expectedReport = createSingleProcessReportForDefinitionAsDefaultUser(
      authorizedProcessDefinition,
      collectionId
    );
    createSingleProcessReportForDefinitionAsDefaultUser(unauthorizedProcessDefinition, collectionId);

    // when
    AuthorizedResolvedCollectionDefinitionDto collection = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetCollectionRequest(collectionId)
      .execute(AuthorizedResolvedCollectionDefinitionDto.class, 200);

    // then
    assertThat(collection.getDefinitionDto().getId(), is(collectionId));
    final List<EntityDto> entities = collection.getDefinitionDto().getData().getEntities();
    assertThat(entities.stream().map(EntityDto::getId).collect(toList()), contains(expectedReport));
  }

  private String createSingleProcessReportForDefinitionAsDefaultUser(final ProcessDefinitionEngineDto processDefinition1,
                                                                     final String collectionId) {
    final ProcessReportDataDto reportDataDto = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinition1.getKey())
      .setProcessDefinitionVersion(processDefinition1.getVersionAsString())
      .setDateInterval(GroupByDateUnit.AUTOMATIC)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE)
      .build();

    final String singleReportId = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(collectionId)
      .execute(IdDto.class, 200)
      .getId();

    final SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
    definitionDto.setData(reportDataDto);

    embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(singleReportId, definitionDto)
      .execute();

    return singleReportId;
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcess(final String definitionKey) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(definitionKey)
      .name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(processModel);
  }
}
