/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static junit.framework.TestCase.assertTrue;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CollectionRestServiceReportsIT extends AbstractIT {

  private static Stream<Integer> definitionTypes() {
    return Stream.of(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION);
  }

  @ParameterizedTest
  @MethodSource("definitionTypes")
  public void getStoredReports(final Integer definitionResourceType) {
    // given
    List<String> expectedReportIds = new ArrayList<>();
    String collectionId1 = createNewCollection();
    expectedReportIds.add(createReportForCollection(collectionId1, definitionResourceType));
    expectedReportIds.add(createReportForCollection(collectionId1, definitionResourceType));

    String collectionId2 = createNewCollection();
    createReportForCollection(collectionId2, definitionResourceType);

    // when
    List<AuthorizedReportDefinitionDto> reports = getReportsForCollectionRequest(collectionId1).executeAndReturnList(
      AuthorizedReportDefinitionDto.class,
      200
    );

    // then
    assertThat(reports.size(), is(expectedReportIds.size()));
    assertTrue(reports.stream()
                 .allMatch(reportDto -> expectedReportIds.contains(reportDto.getDefinitionDto().getId())));
  }

  @Test
  public void getNoneStoredReports() {
    // given
    String collectionId1 = createNewCollection();

    // when
    List<AuthorizedReportDefinitionDto> reports = getReportsForCollectionRequest(collectionId1).executeAndReturnList(
      AuthorizedReportDefinitionDto.class,
      200
    );

    // then
    assertThat(reports.size(), is(0));
  }

  @Test
  public void getReportsForNonExistentCollection() {
    // when
    String response = getReportsForCollectionRequest("someId").execute(String.class, 404);

    // then
    assertTrue(response.contains("Collection does not exist!"));
  }

  private String createNewCollection() {
    return embeddedOptimizeExtension
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

  private OptimizeRequestExecutor getReportsForCollectionRequest(final String collectionId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetReportsForCollectionRequest(collectionId)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD);
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
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .buildCreateSingleDecisionReportRequest(decReport)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewProcessReportAsUser(final SingleProcessReportDefinitionDto procReport) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .buildCreateSingleProcessReportRequest(procReport)
      .execute(IdDto.class, 200)
      .getId();
  }
}
