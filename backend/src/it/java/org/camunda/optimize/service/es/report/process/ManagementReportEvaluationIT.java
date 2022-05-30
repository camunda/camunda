/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.SneakyThrows;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.DEFAULT_TENANT_IDS;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public class ManagementReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String SECOND_TENANT = "secondTenant";
  private static final String FIRST_TENANT = "firstTenant";
  private static final String FIRST_DEF_KEY = "someDef";
  private static final String SECOND_DEF_KEY = "otherDef";

  @Test
  public void managementReportCannotBeCreated() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(FIRST_DEF_KEY));
    final SingleProcessReportDefinitionRequestDto reportDef = createReportDefinition();
    reportDef.getData().setManagementReport(true);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildCreateSingleProcessReportRequest(reportDef)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void managementReportCannotBeEdited() {
    // given
    final ProcessInstanceEngineDto firstInstance =
      engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(FIRST_DEF_KEY));
    final String reportId = createManagementReport(firstInstance);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateSingleReportRequest(reportId, createReportDefinition())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void existingReportsCannotBeConvertedToManagementReports() {
    // given
    final String reportId = reportClient.createAndStoreProcessReport(FIRST_DEF_KEY);
    final SingleProcessReportDefinitionRequestDto updatedDef = createReportDefinition();
    updatedDef.getData().setManagementReport(true);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildUpdateSingleReportRequest(reportId, updatedDef)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void unsavedManagementReportCannotBeEvaluated() {
    // given
    final SingleProcessReportDefinitionRequestDto reportDef = createReportDefinition();
    reportDef.getData().setManagementReport(true);

    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportDef)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void savedManagementReportCanBeEvaluatedAndIncludesAllProcesses() {
    // given
    final ProcessInstanceEngineDto firstInstance =
      engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(FIRST_DEF_KEY));
    engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(SECOND_DEF_KEY));
    importAllEngineEntitiesFromScratch();
    final String reportId = createManagementReport(firstInstance);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse =
      reportClient.evaluateNumberReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.isManagementReport()).isTrue();
    // all available definitions are included in the report definition as it is a management report
    assertThat(resultReportDataDto.getDefinitions()).hasSize(2)
      .extracting(ReportDataDefinitionDto::getKey, ReportDataDefinitionDto::getVersions, ReportDataDefinitionDto::getTenantIds)
      .containsExactlyInAnyOrder(
        Tuple.tuple(FIRST_DEF_KEY, List.of(ALL_VERSIONS), DEFAULT_TENANT_IDS),
        Tuple.tuple(SECOND_DEF_KEY, List.of(ALL_VERSIONS), DEFAULT_TENANT_IDS)
      );
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.NONE);
    final ReportResultResponseDto<Double> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(2L);
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).isEqualTo(2.);
  }

  @Test
  public void savedManagementReportCanBeEvaluatedAndExcludesUnauthorizedProcesses() {
    // given
    final ProcessInstanceEngineDto authorizedProcess =
      engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(FIRST_DEF_KEY));
    // process not authorized for kermit
    engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(SECOND_DEF_KEY));

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      KERMIT_USER, authorizedProcess.getProcessDefinitionKey(), RESOURCE_TYPE_PROCESS_DEFINITION
    );

    importAllEngineEntitiesFromScratch();
    final String reportId = createManagementReport(authorizedProcess, KERMIT_USER, KERMIT_USER);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse = evaluateReportAsKermit(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.isManagementReport()).isTrue();
    // only the process that Kermit can access is included in the result
    assertThat(resultReportDataDto.getDefinitions()).hasSize(1)
      .extracting(ReportDataDefinitionDto::getKey, ReportDataDefinitionDto::getVersions, ReportDataDefinitionDto::getTenantIds)
      .containsExactlyInAnyOrder(
        Tuple.tuple(authorizedProcess.getProcessDefinitionKey(), List.of(ALL_VERSIONS), DEFAULT_TENANT_IDS));
  }

  @Test
  public void savedManagementReportCanBeEvaluatedAndIncludesAllTenants() {
    // given
    engineIntegrationExtension.createTenant(FIRST_TENANT);
    engineIntegrationExtension.createTenant(SECOND_TENANT);
    final ProcessInstanceEngineDto firstInstance =
      engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(FIRST_DEF_KEY), FIRST_TENANT);
    engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(FIRST_DEF_KEY), SECOND_TENANT);
    importAllEngineEntitiesFromScratch();
    final String reportId = createManagementReport(firstInstance);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse =
      reportClient.evaluateNumberReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.isManagementReport()).isTrue();
    assertThat(resultReportDataDto.getDefinitions()).hasSize(1)
      .extracting(ReportDataDefinitionDto::getKey, ReportDataDefinitionDto::getVersions, ReportDataDefinitionDto::getTenantIds)
      .containsExactlyInAnyOrder(
        // the process includes both tenants
        Tuple.tuple(FIRST_DEF_KEY, List.of(ALL_VERSIONS), List.of(FIRST_TENANT, SECOND_TENANT))
      );
    // the result includes data from both tenants
    final ReportResultResponseDto<Double> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(2L);
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).isEqualTo(2.);
  }

  @Test
  public void savedManagementReportCanBeEvaluatedAndExcludesUnauthorizedTenants() {
    // given
    engineIntegrationExtension.createTenant(FIRST_TENANT);
    // kermit not authorized for second tenant
    engineIntegrationExtension.createTenant(SECOND_TENANT);
    final ProcessInstanceEngineDto processInstance =
      engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(FIRST_DEF_KEY), FIRST_TENANT);
    // Kermit is not authorized to see the data for these two instances
    engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(FIRST_DEF_KEY), SECOND_TENANT);
    engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(SECOND_DEF_KEY), SECOND_TENANT);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      KERMIT_USER, processInstance.getProcessDefinitionKey(), RESOURCE_TYPE_PROCESS_DEFINITION
    );
    authorizationClient.grantSingleResourceAuthorizationsForUser(
      KERMIT_USER, FIRST_TENANT, RESOURCE_TYPE_TENANT
    );

    importAllEngineEntitiesFromScratch();
    final String reportId = createManagementReport(processInstance, KERMIT_USER, KERMIT_USER);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse = evaluateReportAsKermit(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.isManagementReport()).isTrue();
    assertThat(resultReportDataDto.getDefinitions()).hasSize(1)
      .extracting(ReportDataDefinitionDto::getKey, ReportDataDefinitionDto::getVersions, ReportDataDefinitionDto::getTenantIds)
      .containsExactlyInAnyOrder(
        // the process includes both tenants
        Tuple.tuple(FIRST_DEF_KEY, List.of(ALL_VERSIONS), List.of(FIRST_TENANT))
      );
    // the result includes data from the authorized tenant
    final ReportResultResponseDto<Double> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(1L);
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).isEqualTo(1.);
  }

  @Test
  public void savedManagementReportCanBeEvaluatedUserNotAuthorizedForAnyImportedProcess() {
    // given
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension.deployAndStartProcess(
      getSingleUserTaskDiagram(FIRST_DEF_KEY));
    engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(SECOND_DEF_KEY));

    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    importAllEngineEntitiesFromScratch();
    final String reportId = createManagementReport(firstInstance, KERMIT_USER, KERMIT_USER);

    // when
    final AuthorizedProcessReportEvaluationResponseDto<Double> evaluationResponse = evaluateReportAsKermit(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.isManagementReport()).isTrue();
    assertThat(resultReportDataDto.getDefinitions()).hasSize(0);
    // the result is empty as Kermit has no authorization for any processes
    final ReportResultResponseDto<Double> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isZero();
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(resultDto.getFirstMeasureData()).isZero();
  }

  private AuthorizedProcessReportEvaluationResponseDto<Double> evaluateReportAsKermit(final String reportId) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSavedReportRequest(reportId)
      // @formatter:off
        .execute(new TypeReference<>() {});}
      // @formatter:on

  private String createManagementReport(final ProcessInstanceEngineDto firstInstance) {
    return createManagementReport(firstInstance, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  @SneakyThrows
  private String createManagementReport(final ProcessInstanceEngineDto firstInstance, final String username,
                                        final String password) {
    // The initial report is created for a specific process
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(firstInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(firstInstance.getProcessDefinitionVersion())
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    final String reportId = reportClient.createSingleProcessReportAsUser(
      new SingleProcessReportDefinitionRequestDto(reportData), username, password);

    final UpdateRequest update = new UpdateRequest()
      .index(ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME)
      .id(reportId)
      .script(new Script(
        ScriptType.INLINE,
        Script.DEFAULT_SCRIPT_LANG,
        "ctx._source.data.managementReport = true",
        Collections.emptyMap()
      ))
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
    elasticSearchIntegrationTestExtension.getOptimizeElasticClient().update(update);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    return reportId;
  }

  private SingleProcessReportDefinitionRequestDto createReportDefinition() {
    return reportClient.createSingleProcessReportDefinitionDto(
      null,
      FIRST_DEF_KEY,
      Collections.emptyList()
    );
  }

}

