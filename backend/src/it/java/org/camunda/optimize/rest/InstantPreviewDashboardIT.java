/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto;
import org.camunda.optimize.service.dashboard.InstantPreviewDashboardService;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto.INSTANT_DASHBOARD_DEFAULT_TEMPLATE;
import static org.camunda.optimize.service.dashboard.InstantPreviewDashboardService.INSTANT_PREVIEW_DASHBOARD_TEMPLATES_PATH;
import static org.camunda.optimize.service.dashboard.InstantPreviewDashboardService.getChecksumCRC32;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public class InstantPreviewDashboardIT extends AbstractDashboardRestServiceIT {

  @Test
  public void instantPreviewDashboardHappyCase() {
    // given
    final InstantPreviewDashboardService instantPreviewDashboardService =
      embeddedOptimizeExtension.getInstantPreviewDashboardService();
    String processDefKey = "dummy";
    String dashboardJsonTemplateFilename = "template2.json";
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(processDefKey));
    importAllEngineEntitiesFromScratch();

    // when
    final Optional<InstantDashboardDataDto> instantPreviewDashboardDtoMaybe =
      instantPreviewDashboardService.createInstantPreviewDashboard(processDefKey, dashboardJsonTemplateFilename);

    // then
    assertThat(instantPreviewDashboardDtoMaybe).isPresent();
    final InstantDashboardDataDto instantPreviewDashboardDto = instantPreviewDashboardDtoMaybe.get();
    assertThat(instantPreviewDashboardDto.getInstantDashboardId()).isEqualTo(
      processDefKey + "_" + dashboardJsonTemplateFilename.replace(".", ""));
    assertThat(instantPreviewDashboardDto.getProcessDefinitionKey()).isEqualTo(processDefKey);
    assertThat(instantPreviewDashboardDto.getTemplateName()).isEqualTo(dashboardJsonTemplateFilename);
    assertThat(instantPreviewDashboardDto.getTemplateHash()).isEqualTo(calculateExpectedChecksum(dashboardJsonTemplateFilename));
    // when
    DashboardDefinitionRestDto returnedDashboard = dashboardClient.getInstantPreviewDashboard(processDefKey, dashboardJsonTemplateFilename);

    // then
    assertThat(returnedDashboard).isNotNull();
    assertThat(returnedDashboard.getId()).isEqualTo(instantPreviewDashboardDto.getDashboardId());
    final DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(returnedDashboard.getId());
    assertThat(dashboard).isNotNull();
    assertThat(dashboard.getReports()).hasSize(5);
  }

  @ParameterizedTest
  @MethodSource("emptyTemplates")
  public void instantPreviewDashboardEmptyTemplateDefaultsToDefault(final String emptyTemplate) {
    // given
    final InstantPreviewDashboardService instantPreviewDashboardService =
      embeddedOptimizeExtension.getInstantPreviewDashboardService();
    String processDefKey = "dummy";
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(processDefKey));
    importAllEngineEntitiesFromScratch();

    // when
    final Optional<InstantDashboardDataDto> instantPreviewDashboardDtoMaybe =
      instantPreviewDashboardService.createInstantPreviewDashboard(processDefKey, emptyTemplate);

    // then
    assertThat(instantPreviewDashboardDtoMaybe).isPresent();
    final InstantDashboardDataDto instantPreviewDashboardDto = instantPreviewDashboardDtoMaybe.get();
    assertThat(instantPreviewDashboardDto.getInstantDashboardId()).isEqualTo(processDefKey + "_" + INSTANT_DASHBOARD_DEFAULT_TEMPLATE.replaceAll("\\.", ""));
    assertThat(instantPreviewDashboardDto.getProcessDefinitionKey()).isEqualTo(processDefKey);
    assertThat(instantPreviewDashboardDto.getTemplateName()).isEqualTo(INSTANT_DASHBOARD_DEFAULT_TEMPLATE);
    assertThat(instantPreviewDashboardDto.getTemplateHash()).isEqualTo(calculateExpectedChecksum(INSTANT_DASHBOARD_DEFAULT_TEMPLATE));

    // when
    DashboardDefinitionRestDto returnedDashboard = dashboardClient.getInstantPreviewDashboard(processDefKey, emptyTemplate);

    // then
    assertThat(returnedDashboard).isNotNull();
    assertThat(returnedDashboard.getId()).isEqualTo(instantPreviewDashboardDto.getDashboardId());
    final DashboardDefinitionRestDto dashboard = dashboardClient.getDashboard(returnedDashboard.getId());
    assertThat(dashboard).isNotNull();
    assertThat(dashboard.getReports()).hasSize(12);
  }

  @Test
  public void instantPreviewDashboardNonExistingDashboard() {
    // given
    String processDefKey = "never_heard_of";
    String dashboardJsonTemplateFilename = "dummy_template.json";

    // when
    String response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetInstantPreviewDashboardRequest(processDefKey, dashboardJsonTemplateFilename)
      .execute(String.class, Response.Status.NOT_FOUND.getStatusCode());

    // then
    assertThat(response).containsSequence("Dashboard does not exist!");
  }

  @Test
  public void getInstantPreviewDashboardWithoutAuthentication() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetInstantPreviewDashboardRequest("bla", "bla")
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void changeInTemplateCausesRefreshOfDashboard() {
    // given
    final InstantPreviewDashboardService instantPreviewDashboardService =
      embeddedOptimizeExtension.getInstantPreviewDashboardService();
    String processDefKey = "dummy";
    String dashboardJsonTemplate = "template1.json";
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(processDefKey));
    importAllEngineEntitiesFromScratch();

    // when
    final Optional<InstantDashboardDataDto> instantPreviewDashboardDtoMaybe =
      instantPreviewDashboardService.createInstantPreviewDashboard(processDefKey, dashboardJsonTemplate);

    // then
    assertThat(instantPreviewDashboardDtoMaybe).isPresent();

    // given
    final InstantDashboardDataDto instantPreviewDashboardDto = instantPreviewDashboardDtoMaybe.get();
    DashboardDefinitionRestDto originalDashboard = dashboardClient.getInstantPreviewDashboard(processDefKey,
                                                                                   dashboardJsonTemplate);
    // Let's keep track of the report IDs that belong to this dashboard, this will be important later
    Set<String> originalReportIds = originalDashboard.getReportIds();

    // when
    // now fiddle with the stored hash in the database so that the code thinks a change has happened
    instantPreviewDashboardDto.setTemplateHash(23L);
    embeddedOptimizeExtension.getInstantPreviewDashboardWriter().saveInstantDashboard(instantPreviewDashboardDto);
    // Perform the check that is done at the start-up from Optimize
    embeddedOptimizeExtension.getInstantPreviewDashboardService().scanForTemplateChanges();
    // Now get the dashboard again
    DashboardDefinitionRestDto newDashboard = dashboardClient.getInstantPreviewDashboard(processDefKey,
                                                                                         dashboardJsonTemplate);

    // then
    //Since the entry had been de-validated, I expect that a new dashboard with a new ID has been created
    assertThat(newDashboard.getId()).isNotEqualTo(originalDashboard.getId());
    // I expect that the reports from the new dashboard were newly generated
    assertThat(newDashboard.getReportIds()).doesNotContainAnyElementsOf(originalReportIds);
    // Moreover I expect the old dashboard to be deleted
    dashboardClient.assertDashboardIsDeleted(originalDashboard.getId());
    // I also expect the old report IDs to be deleted
    assertThat(originalReportIds).allSatisfy(reportId -> reportClient.assertReportIsDeleted(reportId));
  }

  @Test
  public void aCheckForNewDashboardTemplatesDoesntDevalidateCurrentEntries() {
    // given
    final InstantPreviewDashboardService instantPreviewDashboardService =
      embeddedOptimizeExtension.getInstantPreviewDashboardService();
    String processDefKey = "dummy";
    String dashboardJsonTemplate = "template1.json";
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(processDefKey));
    importAllEngineEntitiesFromScratch();

    // when
    final Optional<InstantDashboardDataDto> instantPreviewDashboardDtoMaybe =
      instantPreviewDashboardService.createInstantPreviewDashboard(processDefKey, dashboardJsonTemplate);

    // then
    assertThat(instantPreviewDashboardDtoMaybe).isPresent();

    // given
    DashboardDefinitionRestDto originalDashboard = dashboardClient.getInstantPreviewDashboard(processDefKey,
                                                                                              dashboardJsonTemplate);
    // Let's keep track of the report IDs that belong to this dashboard, this will be important later
    Set<String> originalReportIds = originalDashboard.getReportIds();

    // when
    // Perform the check that is done at the start-up from Optimize
    embeddedOptimizeExtension.getInstantPreviewDashboardService().scanForTemplateChanges();
    // Now get the dashboard again. Since the entry was still valid, I expect the same old dashboard with the same ID
    DashboardDefinitionRestDto newDashboard = dashboardClient.getInstantPreviewDashboard(processDefKey,
                                                                                         dashboardJsonTemplate);

    // then
    assertThat(newDashboard.getId()).isEqualTo(originalDashboard.getId());
    // I expect that the reports from the dashboard also remain the same
    assertThat(newDashboard.getReportIds()).isEqualTo(originalReportIds);
  }

  @Test
  public void existingDashboardsDontGetCreatedAgain() {
    // given
    final InstantPreviewDashboardService instantPreviewDashboardService =
      embeddedOptimizeExtension.getInstantPreviewDashboardService();
    String processDefKey = "dummy";
    String dashboardJsonTemplate = "template1.json";
    engineIntegrationExtension.deployAndStartProcess(getSimpleBpmnDiagram(processDefKey));
    importAllEngineEntitiesFromScratch();

    // when
    final Optional<InstantDashboardDataDto> instantPreviewDashboardDtoMaybe =
      instantPreviewDashboardService.createInstantPreviewDashboard(processDefKey, dashboardJsonTemplate);

    // then
    assertThat(instantPreviewDashboardDtoMaybe).isPresent();

    // given
    DashboardDefinitionRestDto originalDashboard = dashboardClient.getInstantPreviewDashboard(processDefKey,
                                                                                              dashboardJsonTemplate);
    // Let's keep track of the report IDs that belong to this dashboard, this will be important later
    Set<String> originalReportIds = originalDashboard.getReportIds();

    // when
    // Let's retrieve the dashboard again and see what happens
    DashboardDefinitionRestDto hopefullyExistingDashboard = dashboardClient.getInstantPreviewDashboard(processDefKey,
                                                                                              dashboardJsonTemplate);

    // then
    // I expect the same old dashboard with the same ID
    assertThat(hopefullyExistingDashboard.getId()).isEqualTo(originalDashboard.getId());
    // I expect that the reports from the dashboard also remain the same
    assertThat(hopefullyExistingDashboard.getReportIds()).isEqualTo(originalReportIds);
  }

  @NotNull
  private Long calculateExpectedChecksum(final String dashboardJsonTemplate) {
    InputStream templateInputStream = getClass().getClassLoader()
      .getResourceAsStream(INSTANT_PREVIEW_DASHBOARD_TEMPLATES_PATH + dashboardJsonTemplate);
    long checksum = 0L;
    try {
      checksum = getChecksumCRC32(templateInputStream, 8192);
    } catch (IOException e) {
      // Fail test
      assert(false);
    }
    return checksum;
  }

  public static Stream<String> emptyTemplates() {
    return Stream.of("", null);
  }
}
