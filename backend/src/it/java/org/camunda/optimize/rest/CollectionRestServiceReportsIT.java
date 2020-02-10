/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static junit.framework.TestCase.assertTrue;
import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CollectionRestServiceReportsIT extends AbstractIT {

  private static Stream<DefinitionType> definitionTypes() {
    return Stream.of(PROCESS, DECISION);
  }

  @ParameterizedTest
  @MethodSource("definitionTypes")
  public void getStoredReports(final DefinitionType definitionType) {
    // given
    List<String> expectedReportIds = new ArrayList<>();
    String collectionId1 = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    expectedReportIds.add(createReportForCollection(collectionId1, definitionType));
    expectedReportIds.add(createReportForCollection(collectionId1, definitionType));

    String collectionId2 = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    createReportForCollection(collectionId2, definitionType);

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
    String collectionId1 = collectionClient.createNewCollection();

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
    String response = getReportsForCollectionRequest("someId").execute(String.class, Response.Status.NOT_FOUND.getStatusCode());

    // then
    assertTrue(response.contains("Collection does not exist!"));
  }

  @ParameterizedTest(name = "deleting a collection with reports of definition type {0} also deletes containing reports")
  @MethodSource("definitionTypes")
  public void deleteCollectionAlsoDeletesContainingReports(final DefinitionType definitionType) {
    // given
    List<String> expectedReportIds = new ArrayList<>();
    final String collectionId = collectionClient.createNewCollectionWithDefaultScope(definitionType);

    final String reportId1 = createReportForCollection(collectionId, definitionType);
    final String reportId2 = createReportForCollection(collectionId, definitionType);
    expectedReportIds.add(reportId1);
    expectedReportIds.add(reportId2);

    // when
    collectionClient.deleteCollection(collectionId);

    Response report1Response = getReportByIdRequest(reportId1);
    Response report2Response = getReportByIdRequest(reportId2);

    // then
    assertThat(report1Response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
    assertThat(report2Response.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
  }

  private String createReportForCollection(final String collectionId, final DefinitionType definitionType) {
    switch (definitionType) {
      case PROCESS:
        SingleProcessReportDefinitionDto procReport = getProcessReportDefinitionDto(collectionId);
        return createNewProcessReportAsUser(procReport);

      case DECISION:
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
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(DEFAULT_DEFINITION_KEY)
      .setProcessDefinitionVersion("someVersion")
      .setTenantIds(DEFAULT_TENANTS)
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
      .setDecisionDefinitionKey(DEFAULT_DEFINITION_KEY)
      .setDecisionDefinitionVersion("someVersion")
      .setTenantIds(DEFAULT_TENANTS)
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
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private String createNewProcessReportAsUser(final SingleProcessReportDefinitionDto procReport) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .buildCreateSingleProcessReportRequest(procReport)
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  public Response getReportByIdRequest(String reportId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetReportRequest(reportId)
      .execute();
  }
}
