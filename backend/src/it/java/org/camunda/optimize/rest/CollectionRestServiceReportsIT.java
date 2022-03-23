/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.rest.RestTestUtil.getOffsetDiffInHours;
import static org.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_TIMEZONE;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FULLNAME;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;

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

    List<AuthorizedReportDefinitionResponseDto> reports = collectionClient.getReportsForCollection(collectionId1);

    // then
    assertThat(reports)
      .hasSize(expectedReportIds.size())
      .allMatch(reportDto -> expectedReportIds.contains(reportDto.getDefinitionDto().getId()))
      .allMatch(reportDto -> reportDto.getDefinitionDto().getOwner().equals(DEFAULT_FULLNAME))
      .allMatch(reportDto -> reportDto.getDefinitionDto().getLastModifier().equals(DEFAULT_FULLNAME));
  }

  @Test
  public void getStoredReports_adoptTimezoneFromHeader() {
    // given
    OffsetDateTime now = dateFreezer().timezone("Europe/Berlin").freezeDateAndReturn();
    final String collectionId = collectionClient.createNewCollectionWithDefaultScope(DefinitionType.PROCESS);
    createReportForCollection(collectionId, DefinitionType.PROCESS);

    // when
    List<AuthorizedReportDefinitionResponseDto> allReports = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetReportsForCollectionRequest(collectionId)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "Europe/London")
      .executeAndReturnList(AuthorizedReportDefinitionResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(allReports)
      .isNotNull()
      .hasSize(1);
    ReportDefinitionDto reportDefinitionDto = allReports.get(0).getDefinitionDto();
    assertThat(reportDefinitionDto.getCreated()).isEqualTo(now);
    assertThat(reportDefinitionDto.getLastModified()).isEqualTo(now);
    assertThat(getOffsetDiffInHours(reportDefinitionDto.getCreated(), now)).isEqualTo(1.);
    assertThat(getOffsetDiffInHours(reportDefinitionDto.getLastModified(), now)).isEqualTo(1.);
  }

  @Test
  public void getNoneStoredReports() {
    // given
    String collectionId1 = collectionClient.createNewCollection();

    // when
    List<AuthorizedReportDefinitionResponseDto> reports = collectionClient.getReportsForCollection(collectionId1);

    // then
    assertThat(reports).isEmpty();
  }

  @Test
  public void getReportsForNonExistentCollection() {
    // when
    String response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetReportsForCollectionRequest("someId")
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .execute(String.class, Response.Status.NOT_FOUND.getStatusCode());

    // then
    assertThat(response).contains("Collection does not exist!");
  }

  @ParameterizedTest(name = "deleting a collection with reports of definition type {0} also deletes containing reports")
  @MethodSource("definitionTypes")
  public void deleteCollectionAlsoDeletesContainingReports(final DefinitionType definitionType) {
    // given
    final String collectionId = collectionClient.createNewCollectionWithDefaultScope(definitionType);

    final String reportId1 = createReportForCollection(collectionId, definitionType);
    final String reportId2 = createReportForCollection(collectionId, definitionType);

    // when
    collectionClient.deleteCollection(collectionId);

    Response report1Response = reportClient.getSingleReportRawResponse(
      reportId1,
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD
    );
    Response report2Response = reportClient.getSingleReportRawResponse(
      reportId2,
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD
    );

    // then
    assertThat(report1Response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
    assertThat(report2Response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  private String createReportForCollection(final String collectionId, final DefinitionType definitionType) {
    switch (definitionType) {
      case PROCESS:
        SingleProcessReportDefinitionRequestDto procReport = reportClient.createSingleProcessReportDefinitionDto(
          collectionId,
          DEFAULT_DEFINITION_KEY,
          DEFAULT_TENANTS
        );
        return reportClient.createSingleProcessReport(procReport);

      case DECISION:
        SingleDecisionReportDefinitionRequestDto decReport = reportClient.createSingleDecisionReportDefinitionDto(
          collectionId,
          DEFAULT_DEFINITION_KEY,
          DEFAULT_TENANTS
        );
        return reportClient.createSingleDecisionReport(decReport);

      default:
        throw new OptimizeRuntimeException("Unknown resource type provided.");
    }
  }
}
