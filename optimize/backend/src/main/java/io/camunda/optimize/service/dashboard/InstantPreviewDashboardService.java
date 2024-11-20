/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.dashboard;

import static io.camunda.optimize.TomcatConfig.EXTERNAL_SUB_PATH;
import static io.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static io.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto.INSTANT_DASHBOARD_DEFAULT_TEMPLATE;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.STATIC_RESOURCE_PATH;

import io.camunda.optimize.dto.optimize.query.EntityIdResponseDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantIdsDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityType;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionResponseDto;
import io.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import io.camunda.optimize.dto.optimize.rest.export.dashboard.DashboardDefinitionExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.reader.InstantDashboardMetadataReader;
import io.camunda.optimize.service.db.writer.InstantDashboardMetadataWriter;
import io.camunda.optimize.service.entities.EntityImportService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.util.FilenameValidatorUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.Checksum;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class InstantPreviewDashboardService {

  public static final String INSTANT_PREVIEW_DASHBOARD_TEMPLATES_PATH =
      "instant_preview_dashboards/";
  public static final String EXTERNAL_STATIC_RESOURCES_SUBPATH =
      EXTERNAL_SUB_PATH + STATIC_RESOURCE_PATH + "/" + INSTANT_PREVIEW_DASHBOARD_TEMPLATES_PATH;
  public static final String SRC_FIELD = "src";
  public static final String ALTTEXT_FIELD = "altText";
  public static final String TYPE_FIELD = "type";
  public static final String TYPE_IMAGE_VALUE = "image";
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(InstantPreviewDashboardService.class);
  protected final ConfigurationService configurationService;
  private final DashboardService dashboardService;
  private final ReportService reportService;
  private final InstantDashboardMetadataReader instantDashboardMetadataReader;
  private final InstantDashboardMetadataWriter instantDashboardMetadataWriter;
  private final EntityImportService entityImportService;
  private final DefinitionService definitionService;
  private final HashMap<String, Long> templateChecksums;

  public InstantPreviewDashboardService(
      final DashboardService dashboardService,
      final ReportService reportService,
      final InstantDashboardMetadataReader instantDashboardMetadataReader,
      final InstantDashboardMetadataWriter instantDashboardMetadataWriter,
      final EntityImportService entityImportService,
      final DefinitionService definitionService,
      final ConfigurationService configurationService,
      final HashMap<String, Long> templateChecksums) {
    this.dashboardService = dashboardService;
    this.reportService = reportService;
    this.instantDashboardMetadataReader = instantDashboardMetadataReader;
    this.instantDashboardMetadataWriter = instantDashboardMetadataWriter;
    this.entityImportService = entityImportService;
    this.definitionService = definitionService;
    this.configurationService = configurationService;
    this.templateChecksums = templateChecksums;
  }

  public AuthorizedDashboardDefinitionResponseDto getInstantPreviewDashboard(
      final String processDefinitionKey, final String dashboardJsonTemplate, final String userId) {
    final String emptySafeDashboardTemplate =
        StringUtils.isEmpty(dashboardJsonTemplate)
            ? INSTANT_DASHBOARD_DEFAULT_TEMPLATE
            : dashboardJsonTemplate;
    final String processedTemplateName = emptySafeDashboardTemplate.replace(".", "");
    final Optional<String> dashboardId =
        instantDashboardMetadataReader.getInstantDashboardIdFor(
            processDefinitionKey, processedTemplateName);
    if (dashboardId.isPresent()) {
      return dashboardService.getDashboardDefinition(dashboardId.get(), userId);
    } else {
      LOG.info(
          "Instant preview dashboard for process definition [{}] and template [{}} does not exist yet, creating "
              + "it!",
          processDefinitionKey,
          emptySafeDashboardTemplate);
      return createInstantPreviewDashboard(processDefinitionKey, emptySafeDashboardTemplate)
          .map(
              dashboard ->
                  dashboardService.getDashboardDefinition(dashboard.getDashboardId(), userId))
          .orElseThrow(
              () ->
                  new NotFoundException(
                      String.format(
                          "Dashboard does not exist! Either the process definition [%s]"
                              + " or the template [%s] does not exist",
                          processDefinitionKey, emptySafeDashboardTemplate)));
    }
  }

  public Optional<InstantDashboardDataDto> createInstantPreviewDashboard(
      final String processDefinitionKey, final String dashboardJsonTemplate) {
    final String emptySafeDashboardTemplate =
        StringUtils.isEmpty(dashboardJsonTemplate)
            ? INSTANT_DASHBOARD_DEFAULT_TEMPLATE
            : dashboardJsonTemplate;
    final InstantDashboardDataDto instantDashboardDataDto = new InstantDashboardDataDto();
    instantDashboardDataDto.setTemplateName(emptySafeDashboardTemplate);
    instantDashboardDataDto.setProcessDefinitionKey(processDefinitionKey);
    return setupInstantPreviewDashboard(
            instantDashboardDataDto.getTemplateName(), processDefinitionKey)
        .map(
            dashboardId -> {
              instantDashboardDataDto.setDashboardId(dashboardId);
              instantDashboardDataDto.setTemplateHash(
                  templateChecksums.get(emptySafeDashboardTemplate));
              instantDashboardMetadataWriter.saveInstantDashboard(instantDashboardDataDto);
              return instantDashboardDataDto;
            });
  }

  /**
   * This method scans recursively through all the components of a Dashboard Tile of the type "text"
   * and performs a conversion on the target sub-component passed under fieldType. The conversion
   * itself is accomplished by the 'converter', which is the method reference passed under
   * transformFunction. Moreover, the additionalParameter value is also passed onto the converter.
   *
   * @param node Node with which the recursion is started, in the beginning it is the root node of
   *     the Dashboard tile
   * @param fieldType Which node type do we want to transform? E.g. "image", "text", etc
   * @param transformFunction Once a node of the type "fieldType" is found, this function performs
   *     the necessary conversion
   * @param additionalParameterValue An additional string that is used by the converter. In the case
   *     of an image node this is e.g. the clusterId, in the case of a text node it is e.g. the
   *     locale
   */
  public static void findAndConvertTileContent(
      final Object node,
      final String fieldType,
      final BiConsumerWithParameters<Map<String, Object>, String> transformFunction,
      final String additionalParameterValue) {
    if (node instanceof HashMap) {
      final HashMap<String, Object> tileConfigurationElement = (HashMap<String, Object>) node;
      if (fieldType.equals(tileConfigurationElement.getOrDefault(TYPE_FIELD, ""))) {
        // Found the leaf of the specified field type, so perform the transformation
        transformFunction.accept(tileConfigurationElement, additionalParameterValue);
      } else {
        // Otherwise, recursively search again
        tileConfigurationElement.forEach(
            (key, value) ->
                findAndConvertTileContent(
                    value, fieldType, transformFunction, additionalParameterValue));
      }
    } else if (node instanceof ArrayList) {
      // A list typically happens when the "children" node is found. In this case, we want to
      // perform the transformation in each
      // of the children nodes as well
      final ArrayList<Object> list = (ArrayList<Object>) node;
      list.forEach(
          item ->
              findAndConvertTileContent(
                  item, fieldType, transformFunction, additionalParameterValue));
    }
  }

  private Optional<String> setupInstantPreviewDashboard(
      final String dashboardJsonTemplate, final String processDefinitionKey) {
    final Optional<Set<OptimizeEntityExportDto>> exportDtos =
        readAndProcessDashboardTemplate(dashboardJsonTemplate);
    return exportDtos.flatMap(
        exportDtoSet -> createReportsAndAddToDashboard(exportDtoSet, processDefinitionKey));
  }

  private Optional<String> createReportsAndAddToDashboard(
      final Set<OptimizeEntityExportDto> exportDtos, final String processDefinitionKey) {

    final Optional<DefinitionWithTenantIdsDto> processDefinitionWithTenants =
        definitionService.getProcessDefinitionWithTenants(processDefinitionKey);

    processDefinitionWithTenants.ifPresentOrElse(
        processDefinition ->
            exportDtos.stream()
                .filter(SingleProcessReportDefinitionExportDto.class::isInstance)
                .forEach(
                    reportEntity -> {
                      final SingleProcessReportDefinitionExportDto singleReport =
                          ((SingleProcessReportDefinitionExportDto) reportEntity);
                      singleReport.getData().setInstantPreviewReport(true);
                      singleReport
                          .getData()
                          .setDefinitions(
                              List.of(
                                  new ReportDataDefinitionDto(
                                      processDefinitionKey,
                                      processDefinition.getName(),
                                      List.of(ALL_VERSIONS),
                                      processDefinition.getTenantIds())));
                    }),
        () -> LOG.warn("Could not retrieve process definition data for {}", processDefinitionKey));

    exportDtos.stream()
        .filter(DashboardDefinitionExportDto.class::isInstance)
        .forEach(
            dashboardEntity -> {
              final DashboardDefinitionExportDto dashboardData =
                  ((DashboardDefinitionExportDto) dashboardEntity);
              processAllImageUrlsInTiles(dashboardData.getTiles());
              dashboardData.setInstantPreviewDashboard(true);
            });

    final List<EntityIdResponseDto> importedEntities =
        entityImportService.importInstantPreviewEntities(null, exportDtos);
    return importedEntities.stream()
        .filter(entity -> entity.getEntityType() == EntityType.DASHBOARD)
        .findFirst()
        .map(EntityIdResponseDto::getId);
  }

  public void processAllImageUrlsInTiles(final List<DashboardReportTileDto> tiles) {
    tiles.forEach(
        tile -> {
          if (tile.getType() == DashboardTileType.TEXT) {
            final Map<String, Object> textTileConfiguration =
                (Map<String, Object>) tile.getConfiguration();
            // The cluster ID is necessary to calculate the appropriate relative URL
            String clusterId =
                configurationService
                    .getAuthConfiguration()
                    .getCloudAuthConfiguration()
                    .getClusterId();
            if (StringUtils.isNotEmpty(clusterId)) {
              // If we have a clusterId, we need to add it to the URL with a leading slash
              clusterId = "/" + clusterId;
            }
            findAndConvertTileContent(
                textTileConfiguration,
                TYPE_IMAGE_VALUE,
                this::convertAbsoluteUrlsToRelative,
                clusterId);
          }
        });
  }

  private void convertAbsoluteUrlsToRelative(
      final Map<String, Object> textTileConfigElement, final String clusterId) {
    // If working in cloud mode, the relative URL needs to include the cluster ID for it to work
    // properly
    final String srcValue = (String) textTileConfigElement.get(SRC_FIELD);
    final String altTextValue = (String) textTileConfigElement.get(ALTTEXT_FIELD);
    // Extract the file name from the absolute path and put it at the end of the relative path. If
    // working in
    // cloud mode, the relative URL needs to include the cluster ID for it to work properly, since
    // the instant preview
    // dashboard image urls from the template are static and need to be transformed into a relative
    // path that
    // includes the current cluster ID so that e.g. https://www.anyOldUrl.com/MyImage.png becomes
    // /<CLUSTER_ID>/external/static/instant_preview_dashboards/MyImage.png.
    // If a custom context path is specified, we also need to include this in the created URL. If
    // the context path is later
    // changed, these tiles may not work.
    final String srcValueFileName = srcValue.substring((srcValue).lastIndexOf('/') + 1);
    textTileConfigElement.put(
        SRC_FIELD,
        createContextPathAwarePath(
            clusterId + EXTERNAL_STATIC_RESOURCES_SUBPATH + srcValueFileName));
    final String altTextValueFileName = altTextValue.substring((altTextValue).lastIndexOf('/') + 1);
    textTileConfigElement.put(
        ALTTEXT_FIELD,
        createContextPathAwarePath(
            clusterId + EXTERNAL_STATIC_RESOURCES_SUBPATH + altTextValueFileName));
  }

  private Optional<Set<OptimizeEntityExportDto>> readAndProcessDashboardTemplate(
      final String dashboardJsonTemplateFilename) {
    FilenameValidatorUtil.validateFilename(dashboardJsonTemplateFilename);
    final String fullyQualifiedPath =
        INSTANT_PREVIEW_DASHBOARD_TEMPLATES_PATH + dashboardJsonTemplateFilename;
    try (final InputStream dashboardTemplate =
        getClass().getClassLoader().getResourceAsStream(fullyQualifiedPath)) {
      if (dashboardTemplate != null) {
        final String exportedDtoJson =
            new String(dashboardTemplate.readAllBytes(), StandardCharsets.UTF_8);
        final Long checksum = getChecksumCRC32(exportedDtoJson);
        final Set<OptimizeEntityExportDto> valueToBeReturned =
            entityImportService.readExportDtoOrFailIfInvalid(exportedDtoJson);
        templateChecksums.put(dashboardJsonTemplateFilename, checksum);
        return Optional.of(valueToBeReturned);
      } else {
        LOG.error("Could not read dashboard template from " + fullyQualifiedPath);
      }
    } catch (final IOException e) {
      LOG.error("Could not read dashboard template from " + fullyQualifiedPath, e);
    }
    return Optional.empty();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void deleteInstantPreviewDashboardsAndEntitiesForChangedTemplates() {
    final List<Long> templateFileChecksums = getCurrentFileChecksums();
    try {
      final List<String> dashboardsToDelete =
          instantDashboardMetadataWriter.deleteOutdatedTemplateEntriesAndGetExistingDashboardIds(
              templateFileChecksums);
      for (final String dashboardIdToDelete : dashboardsToDelete) {
        final DashboardDefinitionRestDto dashboardDefinition =
            dashboardService.getDashboardDefinitionAsService(dashboardIdToDelete);
        if (dashboardDefinition.isInstantPreviewDashboard()) {
          final Set<String> reportsToDelete = dashboardDefinition.getTileIds();
          for (final String reportId : reportsToDelete) {
            reportService.deleteManagementOrInstantPreviewReport(reportId);
          }
          dashboardService.deleteDashboard(dashboardIdToDelete);
        }
      }
    } catch (final Exception e) {
      // Whichever error occurred in the code above the details of it are contained in the exception
      // message. Catching
      // all possible exceptions here so that Optimize doesn't crash if anything goes wrong with the
      // template checks,
      // since any error is not critical and would not hinder optimize in functioning properly
      LOG.error("There was an error deleting data from an outdated Instant preview dashboard", e);
    }
  }

  public List<Long> getCurrentFileChecksums() {
    // Generate the hashes for the current templates deployed. We assume that the default template
    // exists, and that all other
    // templates are named incrementally
    String currentTemplate = INSTANT_DASHBOARD_DEFAULT_TEMPLATE;
    final List<Long> fileChecksums = new ArrayList<>();
    InputStream templateInputStream =
        getClass()
            .getClassLoader()
            .getResourceAsStream(INSTANT_PREVIEW_DASHBOARD_TEMPLATES_PATH + currentTemplate);
    while (templateInputStream != null) {
      try {
        fileChecksums.add(getChecksumCRC32(templateInputStream, 8192));
      } catch (final IOException e) {
        LOG.error("Could not generate checksum for template [{}]", currentTemplate);
      }
      currentTemplate = incrementFileName(currentTemplate);
      templateInputStream =
          getClass()
              .getClassLoader()
              .getResourceAsStream(INSTANT_PREVIEW_DASHBOARD_TEMPLATES_PATH + currentTemplate);
    }
    return fileChecksums;
  }

  public static long getChecksumCRC32(final InputStream stream, final int bufferSize)
      throws IOException {
    final CheckedInputStream checkedInputStream = new CheckedInputStream(stream, new CRC32());
    final byte[] buffer = new byte[bufferSize];
    while (checkedInputStream.read(buffer, 0, buffer.length) >= 0) {}
    return checkedInputStream.getChecksum().getValue();
  }

  public static long getChecksumCRC32(final String input) {
    final byte[] bytes = input.getBytes();
    final Checksum checksum = new CRC32();
    checksum.update(bytes, 0, bytes.length);
    return checksum.getValue();
  }

  private static String incrementFileName(final String fileName) {
    final String fileNameWithoutExtension = fileName.substring(0, fileName.lastIndexOf("."));
    final String fileExtension = fileName.substring(fileName.lastIndexOf("."));

    final Pattern pattern = Pattern.compile("(.*?)(\\d+)$");
    final Matcher matcher = pattern.matcher(fileNameWithoutExtension);

    // If the file has a number as a suffix, increment it
    if (matcher.find()) {
      final String prefix = matcher.group(1);
      final int suffix;
      try {
        suffix = Integer.parseInt(matcher.group(2)) + 1;
      } catch (final NumberFormatException exception) {
        throw new OptimizeRuntimeException("Error while incrementing file name");
      }
      return prefix + suffix + fileExtension;
    } else {
      // In case the file has no number in the suffix, just add "1" as default
      return fileNameWithoutExtension + "1" + fileExtension;
    }
  }

  private String createContextPathAwarePath(final String subPath) {
    return configurationService
        .getContextPath()
        .map(contextPath -> contextPath + subPath)
        .orElse(subPath);
  }

  @FunctionalInterface
  public interface BiConsumerWithParameters<T, U> {

    void accept(T t, U u);
  }
}
