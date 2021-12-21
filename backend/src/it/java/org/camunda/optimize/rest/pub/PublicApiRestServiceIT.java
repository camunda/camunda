/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.pub;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.PublicApiRestService.QUERY_PARAMETER_ACCESS_TOKEN;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class PublicApiRestServiceIT extends AbstractIT {

  private static final String ACCESS_TOKEN = "1_2_Polizei";

  private String generateValidReport(int numberOfInstances) {
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String reportId = createAndStoreDefaultValidRawProcessReportDefinition(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion()
    );
    //-1 because the call deployAndStartSimpleProcess already creates one process instance
    for (int i = 0; i < numberOfInstances - 1; i++) {
      engineIntegrationExtension.startProcessInstance(processInstance.getDefinitionId());
    }
    importAllEngineEntitiesFromScratch();
    return reportId;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("publicApiRequestWithoutAccessTokenSupplier")
  public void executePublicApiRequestWithoutAuthorization(final String name,
                                                          final Supplier<OptimizeRequestExecutor> apiRequestExecutorSupplier) {
    // given
    // make sure a token is generated but don't use it
    getAccessToken();

    // when executing a public API request without accessToken
    final Response response = apiRequestExecutorSupplier.get().execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("publicApiRequestWithoutRequiredCollectionIdSupplier")
  public void executePublicApiRequestWithoutRequiredCollectionId(final String name,
                                                                 final Supplier<OptimizeRequestExecutor> apiRequestExecutorSupplier) {
    // given
    getAccessToken();

    // when executing a request which usually requires a collectionId without a collectionId
    final Response response = apiRequestExecutorSupplier.get().execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(String.class)).contains("Must specify a collection ID for this request.");
  }

  @Test
  public void importInvalidEntities() {
    // given
    final String collectionId = collectionClient.createNewCollection();
    SingleProcessReportDefinitionRequestDto reportDef = new SingleProcessReportDefinitionRequestDto();
    final SingleProcessReportDefinitionExportDto exportDto = new SingleProcessReportDefinitionExportDto(reportDef);
    exportDto.setId(null);

    // when
    final Response response = publicApiClient.importEntityAndReturnResponse(
      Collections.singleton(exportDto),
      collectionId,
      getAccessToken()
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(ErrorResponseDto.class).getErrorCode()).isEqualTo("importFileInvalid");
    assertThat(response.readEntity(ErrorResponseDto.class).getDetailedMessage())
      .contains("Could not import entities because the provided file contains invalid OptimizeExportDtos.");
  }

  @Test
  public void importInvalidJson() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withoutAuthentication()
      .buildPublicImportEntityDefinitionsRequest(
        Entity.entity("Invalid Json String", MediaType.APPLICATION_JSON_TYPE),
        collectionId,
        getAccessToken()
      )
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(ErrorResponseDto.class).getErrorCode()).isEqualTo("importFileInvalid");
    assertThat(response.readEntity(ErrorResponseDto.class).getDetailedMessage())
      .contains("Could not import entities because the provided file is not a valid list of OptimizeEntityExportDtos.");
  }

  @Test
  public void importEmptyBody() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .withoutAuthentication()
      .buildPublicImportEntityDefinitionsRequest(
        Entity.entity("", MediaType.APPLICATION_JSON_TYPE),
        collectionId,
        getAccessToken()
      )
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(ErrorResponseDto.class).getErrorCode()).isEqualTo("importFileInvalid");
    assertThat(response.readEntity(ErrorResponseDto.class).getDetailedMessage())
      .contains("Could not import entity because the provided file is null or empty.");
  }

  @Test
  public void failGracefullyWhenNoSecretIsConfigured() {

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildPublicExportJsonReportResultRequest("fake_id")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void exportNonExistingReportResult() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .addSingleQueryParam(QUERY_PARAMETER_ACCESS_TOKEN, getAccessToken())
      .buildPublicExportJsonReportResultRequest("IWishIExisted_ButIDont")
      .withoutAuthentication()
      .execute();
    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  private String createAndStoreDefaultValidRawProcessReportDefinition(String processDefinitionKey,
                                                                      String processDefinitionVersion) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    return createAndStoreDefaultProcessReportDefinition(reportData);
  }

  private String createAndStoreDefaultProcessReportDefinition(ProcessReportDataDto reportData) {
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
      new SingleProcessReportDefinitionRequestDto();
    singleProcessReportDefinitionDto.setData(reportData);
    singleProcessReportDefinitionDto.setName("something");
    return createNewProcessReport(singleProcessReportDefinitionDto);
  }

  private String createNewProcessReport(SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return engineIntegrationExtension.deployAndStartProcessWithVariables(getSimpleBpmnDiagram(), new HashMap<>(), null);
  }

  private String getAccessToken() {
    return
      Optional.ofNullable(
        embeddedOptimizeExtension.getConfigurationService().getJsonExportConfiguration().getAccessToken())
        .orElseGet(() -> {
          embeddedOptimizeExtension.getConfigurationService().getJsonExportConfiguration().setAccessToken(ACCESS_TOKEN);
          return ACCESS_TOKEN;
        });
  }

  private Stream<Arguments> publicApiRequestWithoutAccessTokenSupplier() {
    return Stream.of(
      Arguments.of(
        "Export Report Result",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .withoutAuthentication()
          .buildPublicExportJsonReportResultRequest("fake_id")
      ), Arguments.of(
        "Export Report Definition",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .withoutAuthentication()
          .buildPublicExportJsonReportDefinitionRequest(Collections.singletonList("fake_id"), null)
      ),
      Arguments.of(
        "Export Dashboard Definition",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .withoutAuthentication()
          .buildPublicExportJsonDashboardDefinitionRequest(Collections.singletonList("fake_id"), null)
      ),
      Arguments.of(
        "Import Entity",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .withoutAuthentication()
          .buildPublicImportEntityDefinitionsRequest("fake_id", Collections.emptySet(), null)
      ),
      Arguments.of(
        "Delete Report",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .withoutAuthentication()
          .buildPublicDeleteReportRequest("fake_id", null)
      ),
      Arguments.of(
        "Get ReportIds in Collection",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .withoutAuthentication()
          .buildPublicGetAllReportIdsInCollectionRequest("fake_id", null)
      ),
      Arguments.of(
        "Get DashboardIds in Collection",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .withoutAuthentication()
          .buildPublicGetAllDashboardIdsInCollectionRequest("fake_id", null)
      )
    );
  }

  private Stream<Arguments> publicApiRequestWithoutRequiredCollectionIdSupplier() {
    return Stream.of(
      Arguments.of(
        "Import Entity",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .withoutAuthentication()
          .buildPublicImportEntityDefinitionsRequest(null, Collections.emptySet(), ACCESS_TOKEN)
      ),
      Arguments.of(
        "Get ReportIds in Collection",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .withoutAuthentication()
          .buildPublicGetAllReportIdsInCollectionRequest(null, ACCESS_TOKEN)
      ),
      Arguments.of(
        "Get DashboardIds in Collection",
        (Supplier<OptimizeRequestExecutor>) () -> embeddedOptimizeExtension
          .getRequestExecutor()
          .withoutAuthentication()
          .buildPublicGetAllDashboardIdsInCollectionRequest(null, ACCESS_TOKEN)
      )
    );
  }

}