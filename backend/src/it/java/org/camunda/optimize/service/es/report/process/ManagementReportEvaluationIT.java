/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process;

import com.fasterxml.jackson.core.type.TypeReference;
import org.assertj.core.groups.Tuple;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.util.MapResultUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.DEFAULT_TENANT_IDS;
import static org.camunda.optimize.service.dashboard.ManagementDashboardService.ACTIVE_BOTTLENECKS_REPORT_LOCALIZATION_CODE;
import static org.camunda.optimize.service.dashboard.ManagementDashboardService.AUTOMATION_CANDIDATES_REPORT_LOCALIZATION_CODE;
import static org.camunda.optimize.service.dashboard.ManagementDashboardService.AUTOMATION_RATE_REPORT_LOCALIZATION_CODE;
import static org.camunda.optimize.service.dashboard.ManagementDashboardService.INCIDENT_FREE_RATE_REPORT_LOCALIZATION_CODE;
import static org.camunda.optimize.service.dashboard.ManagementDashboardService.LONG_RUNNING_INSTANCES_REPORT_LOCALIZATION_CODE;
import static org.camunda.optimize.service.dashboard.ManagementDashboardService.PROCESS_INSTANCE_USAGE_REPORT_LOCALIZATION_CODE;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.camunda.optimize.util.BpmnModels.VERSION_TAG;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;
import static org.camunda.optimize.util.SuppressionConstants.UNUSED;

public class ManagementReportEvaluationIT extends AbstractProcessDefinitionIT {

  private static final String SECOND_TENANT = "secondTenant";
  private static final String FIRST_TENANT = "firstTenant";
  private static final String FIRST_DEF_KEY = "someDef";
  private static final String SECOND_DEF_KEY = "otherDef";

  @BeforeEach
  public void setup() {
    embeddedOptimizeExtension.getManagementDashboardService().init();
  }

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
    final String reportId = getIdForManagementReport();

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
    engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(FIRST_DEF_KEY));
    engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(SECOND_DEF_KEY));
    importAllEngineEntitiesFromScratch();
    final String reportId = getIdForManagementReport();

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateReport(reportId);

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
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.START_DATE);
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(2L);
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(MapResultUtil.getEntryForKey(
      resultDto.getFirstMeasureData(), embeddedOptimizeExtension.getDateTimeFormatter()
        .format(truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.MONTHS))
    )).isPresent().get().extracting(MapResultEntryDto::getValue).isEqualTo(2.);
  }

  @Test
  public void savedManagementReportCanBeEvaluatedAndIncludesAllProcessesEvenIfProcessHasNoInstanceData() {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(FIRST_DEF_KEY));
    engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(SECOND_DEF_KEY));
    final String defKeyNoInstanceData = "defKeyNoInstanceData";
    engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram(defKeyNoInstanceData));
    // The xml import size is two, so we need to run the import twice to get all the data
    importAllEngineEntitiesFromScratch();
    importAllEngineEntitiesFromLastIndex();
    final String reportId = getIdForManagementReport();

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateReport(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.isManagementReport()).isTrue();
    // all available definitions are included in the report definition as it is a management report
    assertThat(resultReportDataDto.getDefinitions()).hasSize(3)
      .extracting(ReportDataDefinitionDto::getKey, ReportDataDefinitionDto::getVersions, ReportDataDefinitionDto::getTenantIds)
      .containsExactlyInAnyOrder(
        Tuple.tuple(FIRST_DEF_KEY, List.of(ALL_VERSIONS), DEFAULT_TENANT_IDS),
        Tuple.tuple(SECOND_DEF_KEY, List.of(ALL_VERSIONS), DEFAULT_TENANT_IDS),
        Tuple.tuple(defKeyNoInstanceData, List.of(ALL_VERSIONS), DEFAULT_TENANT_IDS)
      );
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.PROCESS_INSTANCE);
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.START_DATE);
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(2L);
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(MapResultUtil.getEntryForKey(
      resultDto.getFirstMeasureData(), embeddedOptimizeExtension.getDateTimeFormatter()
        .format(truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.MONTHS))
    )).isPresent().get().extracting(MapResultEntryDto::getValue).isEqualTo(2.);
  }

  @Test
  public void allManagementReportsCanBeEvaluated() {
    // given
    final String firstDefName = "first";
    engineIntegrationExtension.deployAndStartProcess(createSimpleProcessWithName(FIRST_DEF_KEY, firstDefName));
    final String secondDefName = "second";
    engineIntegrationExtension.deployAndStartProcess(createSimpleProcessWithName(SECOND_DEF_KEY, secondDefName));
    engineIntegrationExtension.deployAndStartDecisionDefinition();
    importAllEngineEntitiesFromScratch();
    final Map<String, SingleProcessReportDefinitionRequestDto> mgmtReportsByName = getAllManagementReports()
      .stream()
      .collect(Collectors.toMap(ReportDefinitionDto::getName, Function.identity()));

    // then
    assertThat(reportClient.evaluateMapReportById(mgmtReportsByName.get(PROCESS_INSTANCE_USAGE_REPORT_LOCALIZATION_CODE).getId()))
      .satisfies(response -> {
        assertThat(response.getReportDefinition().getData().getDefinitions()).extracting(ReportDataDefinitionDto::getKey)
          .containsExactlyInAnyOrder(FIRST_DEF_KEY, SECOND_DEF_KEY);
        assertThat(response.getResult().getData()).isNotEmpty();
      });
    assertThat(reportClient.evaluateNumberReportById(mgmtReportsByName.get(INCIDENT_FREE_RATE_REPORT_LOCALIZATION_CODE).getId()))
      .satisfies(response -> {
        assertThat(response.getReportDefinition().getData().getDefinitions()).extracting(ReportDataDefinitionDto::getKey)
          .containsExactlyInAnyOrder(FIRST_DEF_KEY, SECOND_DEF_KEY);
        assertThat(response.getResult().getData()).isNotNull();
      });
    assertThat(reportClient.evaluateNumberReportById(mgmtReportsByName.get(AUTOMATION_RATE_REPORT_LOCALIZATION_CODE).getId()))
      .satisfies(response -> {
        assertThat(response.getReportDefinition().getData().getDefinitions()).extracting(ReportDataDefinitionDto::getKey)
          .containsExactlyInAnyOrder(FIRST_DEF_KEY, SECOND_DEF_KEY);
        assertThat(response.getResult().getData()).isNotNull();
      });
    assertThat(reportClient.evaluateHyperMapReportById(mgmtReportsByName.get(LONG_RUNNING_INSTANCES_REPORT_LOCALIZATION_CODE)
                                                         .getId()))
      .satisfies(response -> {
        assertThat(response.getReportDefinition().getData().getDefinitions()).extracting(ReportDataDefinitionDto::getKey)
          .containsExactlyInAnyOrder(FIRST_DEF_KEY, SECOND_DEF_KEY);
        assertThat(response.getResult().getData()).isNotEmpty()
          .allSatisfy(result -> assertThat(result.getValue()).isNotEmpty())
          .extracting(HyperMapResultEntryDto::getValue)
          .allSatisfy(hyperResult -> assertThat(hyperResult).extracting(MapResultEntryDto::getLabel)
            .containsExactlyInAnyOrder(firstDefName, secondDefName));
      });
    assertThat(reportClient.evaluateMapReportById(mgmtReportsByName.get(AUTOMATION_CANDIDATES_REPORT_LOCALIZATION_CODE).getId()))
      .satisfies(response -> {
        assertThat(response.getReportDefinition().getData().getDefinitions()).extracting(ReportDataDefinitionDto::getKey)
          .containsExactlyInAnyOrder(FIRST_DEF_KEY, SECOND_DEF_KEY);
        assertThat(response.getResult().getData()).isNotEmpty();
      });
    assertThat(reportClient.evaluateMapReportById(mgmtReportsByName.get(ACTIVE_BOTTLENECKS_REPORT_LOCALIZATION_CODE).getId()))
      .satisfies(response -> {
        assertThat(response.getReportDefinition().getData().getDefinitions()).extracting(ReportDataDefinitionDto::getKey)
          .containsExactlyInAnyOrder(FIRST_DEF_KEY, SECOND_DEF_KEY);
        assertThat(response.getResult().getData()).isNotEmpty();
      });
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
    final String reportId = getIdForManagementReport();

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateReportAsKermit(reportId);

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
    engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(FIRST_DEF_KEY), FIRST_TENANT);
    engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(FIRST_DEF_KEY), SECOND_TENANT);
    importAllEngineEntitiesFromScratch();
    final String reportId = getIdForManagementReport();

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateReport(reportId);

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
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(2L);
    assertThat(resultDto.getFirstMeasureData()).isNotNull();
    assertThat(MapResultUtil.getEntryForKey(
      resultDto.getFirstMeasureData(), embeddedOptimizeExtension.getDateTimeFormatter()
        .format(truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.MONTHS))
    )).isPresent().get().extracting(MapResultEntryDto::getValue).isEqualTo(2.);
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
    final String reportId = getIdForManagementReport();

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateReportAsKermit(reportId);

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
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(1L);
    assertThat(MapResultUtil.getEntryForKey(
      resultDto.getFirstMeasureData(), embeddedOptimizeExtension.getDateTimeFormatter()
        .format(truncateToStartOfUnit(OffsetDateTime.now(), ChronoUnit.MONTHS))
    )).isPresent().get().extracting(MapResultEntryDto::getValue).isEqualTo(1.);
  }

  @Test
  public void savedManagementReportCanBeEvaluatedUserNotAuthorizedForAnyImportedProcess() {
    // given
    final ProcessInstanceEngineDto firstInstance = engineIntegrationExtension.deployAndStartProcess(
      getSingleUserTaskDiagram(FIRST_DEF_KEY));
    engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(SECOND_DEF_KEY));

    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    importAllEngineEntitiesFromScratch();
    final String reportId = getIdForManagementReport();

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      evaluateReportAsKermit(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.isManagementReport()).isTrue();
    assertThat(resultReportDataDto.getDefinitions()).hasSize(0);
    // the result is empty as Kermit has no authorization for any processes
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isZero();
    assertThat(resultDto.getFirstMeasureData()).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("localizedReportNames")
  public void managementReportNamesAreLocalized(final String locale, final Set<String> expectedReportNames) {
    // given
    engineIntegrationExtension.deployAndStartProcess(getSingleUserTaskDiagram(FIRST_DEF_KEY));
    importAllEngineEntitiesFromScratch();

    // when
    final Set<String> localizedReportNames = getAllManagementReports().stream()
      .map(report -> reportClient.evaluateProcessReportLocalized(report.getId(), locale).getReportDefinition().getName())
      .collect(toSet());

    // then
    assertThat(localizedReportNames).containsExactlyInAnyOrderElementsOf(expectedReportNames);
  }

  private AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateReport(final String reportId) {
    return evaluateReportAsUser(reportId, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  private AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateReportAsKermit(final String reportId) {
    return evaluateReportAsUser(reportId, KERMIT_USER, KERMIT_USER);
  }

  private AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateReportAsUser(final String reportId,
                                                                                                     final String username,
                                                                                                     final String password) {
    return embeddedOptimizeExtension.getRequestExecutor()
      .withUserAuthentication(username, password)
      .buildEvaluateSavedReportRequest(reportId)
      // @formatter:off
      .execute(new TypeReference<>() {});
    // @formatter:on
  }

  private String getIdForManagementReport() {
    return getAllManagementReports()
      .stream()
      .filter(report -> report.getName().equals(PROCESS_INSTANCE_USAGE_REPORT_LOCALIZATION_CODE))
      .findFirst()
      .map(ReportDefinitionDto::getId)
      .orElseThrow(() -> new OptimizeIntegrationTestException("Cannot find any management reports"));
  }

  private List<SingleProcessReportDefinitionRequestDto> getAllManagementReports() {
    return elasticSearchIntegrationTestExtension.getAllDocumentsOfIndexAs(
      SINGLE_PROCESS_REPORT_INDEX_NAME, SingleProcessReportDefinitionRequestDto.class);
  }

  private SingleProcessReportDefinitionRequestDto createReportDefinition() {
    return reportClient.createSingleProcessReportDefinitionDto(
      null,
      FIRST_DEF_KEY,
      Collections.emptyList()
    );
  }

  private BpmnModelInstance createSimpleProcessWithName(final String processDefKey, final String processName) {
    return Bpmn.createExecutableProcess(processDefKey)
      .camundaVersionTag(VERSION_TAG)
      .name(processName)
      .startEvent("startEvent")
      .userTask("userTask")
      .endEvent("endEvent")
      .done();
  }

  @SuppressWarnings(UNUSED)
  private static Stream<Arguments> localizedReportNames() {
    return Stream.of(
      Arguments.of(
        "en",
        Set.of(
          "Process Instance Usage",
          "Overall Incident-Free Rate",
          "Automation Rate (<1 hour)",
          "Long-Running Process Instances",
          "Automation Candidates",
          "Active Bottlenecks"
        )
      ),
      Arguments.of(
        "de",
        Set.of(
          "Anzahl der ausgeführten Prozesseinstanzen",
          "Prozessausführungen ohne Zwischenfälle",
          "Automatisierungsrate (< 1 Stunde)",
          "Lang laufende Prozessinstanzen",
          "Automatisierungskandidaten",
          "Aktive Engpässe"
        )
      )
    );
  }

}

