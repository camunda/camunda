/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import junitparams.JUnitParamsRunner;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(JUnitParamsRunner.class)
public class CollectionReportsAuthorizationIT {
  @RegisterExtension
  @Order(1)
  public static ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule =
    new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public static EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public static EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  protected AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtensionRule);

  private static final Object[] definitionTypes() {
    return new Object[]{RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION};
  }

  private static final Object[][] definitionTypePairs() {
    return new Object[][]{
      {Arrays.asList(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION)},
      {Arrays.asList(RESOURCE_TYPE_DECISION_DEFINITION, RESOURCE_TYPE_PROCESS_DEFINITION)}
    };
  }

  @ParameterizedTest
  @MethodSource("definitionTypes")
  public void getReportsForAuthorizedCollection(final int definitionType) {
    // given
    final String collectionId1 = createNewCollection();
    List<String> expectedReportIds = new ArrayList<>();
    expectedReportIds.add(createReportForCollection(collectionId1, definitionType));
    expectedReportIds.add(createReportForCollection(collectionId1, definitionType));

    final String collectionId2 = createNewCollection();
    createReportForCollection(collectionId2, definitionType);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionType);
    addRoleToCollectionAsDefaultUser(new CollectionRoleDto(new UserDto(KERMIT_USER), RoleType.VIEWER), collectionId1);

    // when
    List<AuthorizedReportDefinitionDto> reports =
      getReportsForCollectionRequestAsKermit(collectionId1).executeAndReturnList(
        AuthorizedReportDefinitionDto.class,
        200
      );

    // then
    assertThat(reports.size(), is(expectedReportIds.size()));
    assertTrue(reports.stream()
                 .allMatch(reportDto -> expectedReportIds.contains(reportDto.getDefinitionDto().getId())));
  }

  @ParameterizedTest
  @MethodSource("definitionTypePairs")
  public void getReportsForPartiallyAuthorizedCollection(final List<Integer> typePair) {
    // given
    final String collectionId1 = createNewCollection();
    List<String> expectedReportIds = new ArrayList<>();
    expectedReportIds.add(createReportForCollection(collectionId1, typePair.get(0)));
    expectedReportIds.add(createReportForCollection(collectionId1, typePair.get(0)));
    createReportForCollection(collectionId1, typePair.get(1));

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(typePair.get(0));
    addRoleToCollectionAsDefaultUser(new CollectionRoleDto(new UserDto(KERMIT_USER), RoleType.VIEWER), collectionId1);

    // when
    List<AuthorizedReportDefinitionDto> allAlerts =
      getReportsForCollectionRequestAsKermit(collectionId1).executeAndReturnList(
        AuthorizedReportDefinitionDto.class,
        200
      );

    // then
    assertThat(allAlerts.size(), is(expectedReportIds.size()));
    assertTrue(allAlerts.stream()
                 .allMatch(reportDto -> expectedReportIds.contains(reportDto.getDefinitionDto().getId())));
  }

  @ParameterizedTest
  @MethodSource("definitionTypes")
  public void getReportsForUnauthorizedCollection(final int definitionResourceType) {
    // given
    final String collectionId1 = createNewCollection();
    List<String> expectedReportIds = new ArrayList<>();
    expectedReportIds.add(createReportForCollection(collectionId1, definitionResourceType));
    expectedReportIds.add(createReportForCollection(collectionId1, definitionResourceType));

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);

    // when
    Response response = getReportsForCollectionRequestAsKermit(collectionId1).execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  private String createNewCollection() {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createReportForCollection(final String collectionId, final int resourceType) {
    switch (resourceType) {
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        SingleProcessReportDefinitionDto procReport = getProcessReportDefinitionDto(collectionId);
        return createNewProcessReportAsUser(procReport);

      case RESOURCE_TYPE_DECISION_DEFINITION:
        SingleDecisionReportDefinitionDto decReport = getDecisionReportDefinitionDto(collectionId);
        return createNewDecisionReportAsUser(decReport);

      default:
        throw new OptimizeRuntimeException("Unknown resource type provided.");
    }
  }

  private SingleProcessReportDefinitionDto getProcessReportDefinitionDto(final String collectionId) {
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey("someKey")
      .setProcessDefinitionVersion("someVersion")
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    report.setName("aProcessReport");
    report.setCollectionId(collectionId);
    return report;
  }

  private SingleDecisionReportDefinitionDto getDecisionReportDefinitionDto(final String collectionId) {
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("someKey")
      .setDecisionDefinitionVersion("someVersion")
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();

    SingleDecisionReportDefinitionDto report = new SingleDecisionReportDefinitionDto();
    report.setData(reportData);
    report.setName("aDecisionReport");
    report.setCollectionId(collectionId);
    return report;
  }

  private String createNewDecisionReportAsUser(final SingleDecisionReportDefinitionDto decReport) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .buildCreateSingleDecisionReportRequest(decReport)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewProcessReportAsUser(final SingleProcessReportDefinitionDto procReport) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .buildCreateSingleProcessReportRequest(procReport)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String addRoleToCollectionAsDefaultUser(final CollectionRoleDto roleDto,
                                                  final String collectionId) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildAddRoleToCollectionRequest(collectionId, roleDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private OptimizeRequestExecutor getReportsForCollectionRequestAsKermit(final String collectionId) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetReportsForCollectionRequest(collectionId)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER);
  }
}
