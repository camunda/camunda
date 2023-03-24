/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.dashboard;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.EntityIdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionResponseDto;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.dto.optimize.rest.export.dashboard.DashboardDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.entities.EntityImportService;
import org.camunda.optimize.service.es.reader.InstantDashboardMetadataReader;
import org.camunda.optimize.service.es.writer.InstantDashboardMetadataWriter;
import org.camunda.optimize.service.report.ReportService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.Checksum;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto.INSTANT_DASHBOARD_DEFAULT_TEMPLATE;

@Slf4j
@AllArgsConstructor
@Component
public class InstantPreviewDashboardService {

  public static final String INSTANT_PREVIEW_DASHBOARD_TEMPLATES_PATH = "instant_preview_dashboards/";

  private final DashboardService dashboardService;
  private final ReportService reportService;
  private final InstantDashboardMetadataReader instantDashboardMetadataReader;
  private final InstantDashboardMetadataWriter instantDashboardMetadataWriter;
  private final EntityImportService entityImportService;
  private final DefinitionService definitionService;
  private HashMap<String, Long> templateChecksums;

  public AuthorizedDashboardDefinitionResponseDto getInstantPreviewDashboard(final String processDefinitionKey,
                                                                             final String dashboardJsonTemplate,
                                                                             final String userId) {
    String emptySafeDashboardTemplate = StringUtils.isEmpty(dashboardJsonTemplate)
      ? INSTANT_DASHBOARD_DEFAULT_TEMPLATE : dashboardJsonTemplate;
    String processedTemplateName = emptySafeDashboardTemplate.replace(".", "");
    Optional<String> dashboardIdMaybe = instantDashboardMetadataReader.getInstantDashboardIdFor(
      processDefinitionKey,
      processedTemplateName
    );
    if (dashboardIdMaybe.isPresent()) {
      return dashboardService.getDashboardDefinition(dashboardIdMaybe.get(), userId);
    } else {
      log.info("Instant preview dashboard for process definition [{}] and template [{}} does not exist yet, creating " +
                 "it!", processDefinitionKey, emptySafeDashboardTemplate);
      final Optional<InstantDashboardDataDto> newDashboard = createInstantPreviewDashboard(
        processDefinitionKey,
        emptySafeDashboardTemplate
      );
      if (newDashboard.isPresent()) {
        return dashboardService.getDashboardDefinition(newDashboard.get().getDashboardId(), userId);
      } else {
        throw new NotFoundException(
          String.format("Dashboard does not exist! Either the process definition [%s]" +
                          " or the template [%s] does not exist", processDefinitionKey, emptySafeDashboardTemplate));
      }
    }
  }

  public Optional<InstantDashboardDataDto> createInstantPreviewDashboard(final String processDefinitionKey,
                                                                         final String dashboardJsonTemplate) {
    String emptySafeDashboardTemplate = StringUtils.isEmpty(dashboardJsonTemplate)
      ? INSTANT_DASHBOARD_DEFAULT_TEMPLATE : dashboardJsonTemplate;
    final InstantDashboardDataDto instantDashboardDataDto = new InstantDashboardDataDto();
    instantDashboardDataDto.setTemplateName(emptySafeDashboardTemplate);
    instantDashboardDataDto.setProcessDefinitionKey(processDefinitionKey);
    return setupInstantPreviewDashboard(instantDashboardDataDto.getTemplateName(), processDefinitionKey)
      .map(dashboardId -> {
        instantDashboardDataDto.setDashboardId(dashboardId);
        instantDashboardDataDto.setTemplateHash(templateChecksums.get(emptySafeDashboardTemplate));
        instantDashboardMetadataWriter.saveInstantDashboard(instantDashboardDataDto);
        return instantDashboardDataDto;
      });
  }

  private Optional<String> setupInstantPreviewDashboard(final String dashboardJsonTemplate,
                                                        final String processDefinitionKey) {
    Optional<Set<OptimizeEntityExportDto>> exportDtos = readAndProcessDashboardTemplate(dashboardJsonTemplate);
    return exportDtos.flatMap(exportDtoSet -> createReportsAndAddToDashboard(exportDtoSet, processDefinitionKey));
  }

  private Optional<String> createReportsAndAddToDashboard(final Set<OptimizeEntityExportDto> exportDtos,
                                                          final String processDefinitionKey) {

    final Optional<DefinitionWithTenantIdsDto> processDefinitionWithTenants =
      definitionService.getProcessDefinitionWithTenants(processDefinitionKey);

    processDefinitionWithTenants
      .ifPresentOrElse(
        processDefinition ->
          exportDtos.stream()
            .filter(SingleProcessReportDefinitionExportDto.class::isInstance)
            .forEach(reportEntity -> {
              SingleProcessReportDefinitionExportDto singleReport =
                ((SingleProcessReportDefinitionExportDto) reportEntity);
              singleReport.getData().setDefinitions(
                List.of(new ReportDataDefinitionDto(
                  processDefinitionKey, List.of(ALL_VERSIONS), processDefinition.getTenantIds()
                )));
              singleReport.getData().setInstantPreviewReport(true);
            }),
        () -> log.warn("Could not retrieve process definition data for {}", processDefinitionKey)
      );

    exportDtos.stream()
      .filter(DashboardDefinitionExportDto.class::isInstance)
      .forEach(dashboardEntity -> {
        DashboardDefinitionExportDto dashboardData = ((DashboardDefinitionExportDto) dashboardEntity);
        dashboardData.setInstantPreviewDashboard(true);
      });

    final List<EntityIdResponseDto> importedEntities =
      entityImportService.importInstantPreviewEntities(
        null,
        exportDtos
      );
    return importedEntities
      .stream()
      .filter(entity -> entity.getEntityType() == EntityType.DASHBOARD)
      .findFirst()
      .map(EntityIdResponseDto::getId);
  }

  private Optional<Set<OptimizeEntityExportDto>> readAndProcessDashboardTemplate(
    final String dashboardJsonTemplateFilename) {
    try (InputStream dashboardTemplate = this.getClass()
      .getClassLoader()
      .getResourceAsStream(INSTANT_PREVIEW_DASHBOARD_TEMPLATES_PATH + dashboardJsonTemplateFilename)) {
      if (dashboardTemplate != null) {
        String exportedDtoJson = new String(dashboardTemplate.readAllBytes(), StandardCharsets.UTF_8);
        Long checksum = getChecksumCRC32(exportedDtoJson);
        final Set<OptimizeEntityExportDto> valueToBeReturned = entityImportService.readExportDtoOrFailIfInvalid(
          exportedDtoJson);
        templateChecksums.put(dashboardJsonTemplateFilename, checksum);
        return Optional.of(valueToBeReturned);
      } else {
        log.error("Could not read dashboard template from " + INSTANT_PREVIEW_DASHBOARD_TEMPLATES_PATH + dashboardJsonTemplateFilename);
      }
    } catch (IOException e) {
      log.error(
        "Could not read dashboard template from " + INSTANT_PREVIEW_DASHBOARD_TEMPLATES_PATH + dashboardJsonTemplateFilename,
        e
      );
    }
    return Optional.empty();
  }

  @EventListener(ApplicationReadyEvent.class)
  /*
    This method checks at startup if any of the templates have changed. Should any template have changed, this will
    trigger the removal of the Instant Preview Dashboard entry as well as the deletion of the related dashboards and
    reports. Since there is no way with spring boot to check all the files in the "resources" folder, we're assuming
    the following convention for the template name: templateN.json , where N is any positive integer. We will scan
    the folder as long as we keep finding templates, so we start with template1.json, template2.json,..., templateN
    .json. When we don't find any more templates to process we stop.
   */
  public void scanForTemplateChanges() {
    // Generate the hashes for the current templates deployed

    String currentTemplate = INSTANT_DASHBOARD_DEFAULT_TEMPLATE;
    List<Long> fileChecksums = new ArrayList<>();
    InputStream templateInputStream = getClass().getClassLoader()
      .getResourceAsStream(INSTANT_PREVIEW_DASHBOARD_TEMPLATES_PATH + currentTemplate);
    while (templateInputStream != null) {
      try {
        fileChecksums.add(getChecksumCRC32(templateInputStream, 8192));
      } catch (IOException e) {
        log.error("Could not generate checksum for template [{}]", currentTemplate);
      }
      currentTemplate = incrementFileName(currentTemplate);
      templateInputStream = getClass().getClassLoader()
        .getResourceAsStream(INSTANT_PREVIEW_DASHBOARD_TEMPLATES_PATH + currentTemplate);
    }
    try {
      List<String> dashboardsToDelete = instantDashboardMetadataWriter.deleteOutdatedTemplateEntries(fileChecksums);
      for (String dashboardIdToDelete : dashboardsToDelete) {
        final DashboardDefinitionRestDto dashboardDefinition =
          dashboardService.getDashboardDefinitionAsService(dashboardIdToDelete);
        if (dashboardDefinition.isInstantPreviewDashboard()) {
          final Set<String> reportsToDelete = dashboardDefinition.getTileIds();
          for (String reportId : reportsToDelete) {
            reportService.deleteManagementOrInstantPreviewReport(reportId);
          }
          dashboardService.deleteDashboard(dashboardIdToDelete);
        }
      }
    } catch (Exception e) {
      // Whichever error occurred in the code above the details of it are contained in the exception message. Catching
      // all possible exceptions here so that Optimize doesn't crash if anything goes wrong with the template checks,
      // since any error is not critical and would not hinder optimize in functioning properly
      log.error("There was an error deleting data from an outdated Instant Preview Dashboard", e);
    }
  }

  public static long getChecksumCRC32(InputStream stream, int bufferSize) throws IOException {
    CheckedInputStream checkedInputStream = new CheckedInputStream(stream, new CRC32());
    byte[] buffer = new byte[bufferSize];
    while (checkedInputStream.read(buffer, 0, buffer.length) >= 0) ;
    return checkedInputStream.getChecksum().getValue();
  }

  public static long getChecksumCRC32(String input) {
    byte[] bytes = input.getBytes();
    Checksum checksum = new CRC32();
    checksum.update(bytes, 0, bytes.length);
    return checksum.getValue();
  }

  /**
   * This method returns the name of the next file to process by incrementing the current file name by one
   *
   * @param fileName: name of the file to increment, e.g. template2.json
   * @return name of the fileName with the suffix incremented by 1, e.g. template3.json
   */
  private static String incrementFileName(String fileName) {
    String fileNameWithoutExtension = fileName.substring(0, fileName.lastIndexOf("."));
    String fileExtension = fileName.substring(fileName.lastIndexOf("."));

    Pattern pattern = Pattern.compile("(.*?)(\\d+)$");
    Matcher matcher = pattern.matcher(fileNameWithoutExtension);

    // If the file has a number as suffix, increment it
    if (matcher.find()) {
      String prefix = matcher.group(1);
      int suffix = Integer.parseInt(matcher.group(2)) + 1;
      return prefix + suffix + fileExtension;
    } else {
      // In case the file has no number in the suffix, just add "1" as default
      return fileNameWithoutExtension + "1" + fileExtension;
    }
  }
}
