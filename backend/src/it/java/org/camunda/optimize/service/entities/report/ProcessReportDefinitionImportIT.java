/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.entities.report;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.EntityIdResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionExceptionItemDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionExceptionResponseDto;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.dto.optimize.rest.ImportIndexMismatchDto;
import org.camunda.optimize.dto.optimize.rest.ImportedIndexMismatchResponseDto;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.CombinedProcessReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.service.entities.AbstractExportImportEntityDefinitionIT;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FIRSTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_LASTNAME;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;

public class ProcessReportDefinitionImportIT extends AbstractExportImportEntityDefinitionIT {

  private static Stream<String> specialVersionKeywords() {
    return Stream.of(ALL_VERSIONS, LATEST_VERSION);
  }

  @ParameterizedTest
  @MethodSource("getTestProcessReports")
  public void importReport(final SingleProcessReportDefinitionRequestDto reportDefToImport) {
    // given
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    dateFreezer().freezeDateAndReturn();

    // when
    final EntityIdResponseDto importedId = importClient.importEntityAndReturnId(createExportDto(reportDefToImport));

    // then
    final SingleProcessReportDefinitionRequestDto importedReport =
      (SingleProcessReportDefinitionRequestDto) reportClient.getReportById(importedId.getId());

    assertImportedReport(importedReport, reportDefToImport, null);
  }

  @Test
  public void importReportWithInvalidDescription() {
    // given
    final SingleProcessReportDefinitionExportDto simpleProcessExportDto = createSimpleProcessExportDto();
    simpleProcessExportDto.setDescription("");

    // when
    final Response response = importClient.importEntity(simpleProcessExportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    final ErrorResponseDto invalidDescriptionResponseDto = response.readEntity(ErrorResponseDto.class);
    assertThat(invalidDescriptionResponseDto.getErrorCode()).isEqualTo("importDescriptionInvalid");
  }

  @Test
  public void importReport_incorrectIndexVersion() {
    // given a report with report index version different from the current version
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();
    exportedReportDto.setSourceIndexVersion(SingleProcessReportIndex.VERSION + 1);

    // when
    final Response response = importClient.importEntity(exportedReportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    final ImportedIndexMismatchResponseDto importedIndexMismatchResponseDto =
      response.readEntity(ImportedIndexMismatchResponseDto.class);
    assertThat(importedIndexMismatchResponseDto.getErrorCode()).isEqualTo("importIndexVersionMismatch");
    assertThat(importedIndexMismatchResponseDto.getMismatchingIndices())
      .hasSize(1)
      .containsExactly(
        ImportIndexMismatchDto.builder()
          .indexName(embeddedOptimizeExtension.getIndexNameService()
                       .getOptimizeIndexNameWithVersion(new SingleProcessReportIndex()))
          .sourceIndexVersion(SingleProcessReportIndex.VERSION + 1)
          .targetIndexVersion(SingleProcessReportIndex.VERSION)
          .build());
  }

  @Test
  public void importReport_missingDefinition() {
    // given a report for a definition that doesn't exist
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();

    // when
    final Response response = importClient.importEntity(exportedReportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    final DefinitionExceptionResponseDto definitionExceptionResponseDto =
      response.readEntity(DefinitionExceptionResponseDto.class);
    assertThat(definitionExceptionResponseDto.getErrorCode()).isEqualTo("importDefinitionDoesNotExist");
    assertThat(definitionExceptionResponseDto.getDefinitions())
      .hasSize(1)
      .containsExactly(
        DefinitionExceptionItemDto.builder()
          .type(DefinitionType.PROCESS)
          .key(DEFINITION_KEY)
          .versions(Collections.singletonList(DEFINITION_VERSION))
          .tenantIds(Collections.singletonList(null))
          .build());
  }

  @Test
  public void importReport_missingAllRequiredVersions() {
    // given a definition that only exists with version 1 and a report that requires version 5 of this definition
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();
    exportedReportDto.getData().setProcessDefinitionVersion("5");

    // when
    final Response response = importClient.importEntity(exportedReportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    final DefinitionExceptionResponseDto definitionExceptionResponseDto =
      response.readEntity(DefinitionExceptionResponseDto.class);
    assertThat(definitionExceptionResponseDto.getErrorCode()).isEqualTo("importDefinitionDoesNotExist");
    assertThat(definitionExceptionResponseDto.getDefinitions())
      .hasSize(1)
      .containsExactly(
        DefinitionExceptionItemDto.builder()
          .type(DefinitionType.PROCESS)
          .key(DEFINITION_KEY)
          .versions(Collections.singletonList("5"))
          .tenantIds(Collections.singletonList(null))
          .build());
  }

  @ParameterizedTest
  @MethodSource("specialVersionKeywords")
  public void importReport_allOrLatestVersion(final String versionString) {
    // given
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();
    exportedReportDto.getData().setProcessDefinitionVersion(versionString);

    // when
    final EntityIdResponseDto importedId = importClient.importEntityAndReturnId(exportedReportDto);

    // then
    final SingleProcessReportDefinitionRequestDto importedReport =
      (SingleProcessReportDefinitionRequestDto) reportClient.getReportById(importedId.getId());
    assertThat(importedReport.getData().getDefinitionVersions()).containsExactly(versionString);
  }

  @ParameterizedTest
  @MethodSource("specialVersionKeywords")
  public void importReport_allOrLatestVersion_missingAllVersions(final String versionString) {
    // given
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();
    exportedReportDto.getData().setProcessDefinitionVersion(versionString);

    // when
    final Response response = importClient.importEntity(exportedReportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(DefinitionExceptionResponseDto.class).getErrorCode())
      .isEqualTo("importDefinitionDoesNotExist");
    assertThat(response.readEntity(DefinitionExceptionResponseDto.class).getDefinitions())
      .hasSize(1)
      .containsExactly(
        DefinitionExceptionItemDto.builder()
          .type(DefinitionType.PROCESS)
          .key(DEFINITION_KEY)
          .versions(Collections.singletonList(versionString))
          .tenantIds(Collections.singletonList(null))
          .build());
  }

  @Test
  public void importReport_partiallyMissingVersions() {
    // given a definition that only exists with version 1 and 3
    // and a report that requires version 1,2,3,5 of this definition
    createAndSaveDefinition(DefinitionType.PROCESS, null, "1");
    createAndSaveDefinition(DefinitionType.PROCESS, null, "3");
    final OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    final SingleProcessReportDefinitionRequestDto reportDefinitionToImport = createSimpleProcessReportDefinition();
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();
    exportedReportDto.getData().setProcessDefinitionVersions(Lists.newArrayList("1", "2", "3", "5"));
    exportedReportDto.getData().getConfiguration().setXml("oldXml");

    // when
    final EntityIdResponseDto importedId = importClient.importEntityAndReturnId(exportedReportDto);

    // then all non version related data is accurate
    final SingleProcessReportDefinitionRequestDto importedReport =
      (SingleProcessReportDefinitionRequestDto) reportClient.getReportById(importedId.getId());

    assertThat(importedReport.getOwner()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(importedReport.getLastModifier()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(importedReport.getCreated()).isEqualTo(now);
    assertThat(importedReport.getLastModified()).isEqualTo(now);
    assertThat(importedReport.getCollectionId()).isNull();
    assertThat(importedReport.getName()).isEqualTo(reportDefinitionToImport.getName());
    assertThat(importedReport.getData())
      .usingRecursiveComparison()
      .ignoringFields(SingleReportDataDto.Fields.definitions + "." + ReportDataDefinitionDto.Fields.versions)
      .ignoringFields(SingleReportDataDto.Fields.configuration)
      .isEqualTo(reportDefinitionToImport.getData());
    assertThat(importedReport.getData().getConfiguration())
      .usingRecursiveComparison()
      .ignoringFields(SingleReportConfigurationDto.Fields.xml)
      .isEqualTo(reportDefinitionToImport.getData().getConfiguration());

    // nonexistent versions have been removed from the version list
    assertThat(importedReport.getData().getDefinitionVersions()).containsExactly("1", "3");

    // and the XML has been updated to reflect the latest existing version's XML
    assertThat(importedReport.getData().getConfiguration().getXml())
      .isEqualTo(DEFINITION_XML_STRING + "3");
  }

  @Test
  public void importReport_missingTenant() {
    // given a definition that exists on tenant1 and a report that requires it to exist on tenant2
    engineIntegrationExtension.createTenant("tenant1");
    engineIntegrationExtension.createTenant("tenant2");
    importAllEngineEntitiesFromScratch();

    createAndSaveDefinition(DefinitionType.PROCESS, "tenant1");
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();
    exportedReportDto.getData().setTenantIds(Collections.singletonList("tenant2"));

    // when
    final Response response = importClient.importEntity(exportedReportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(DefinitionExceptionResponseDto.class).getErrorCode())
      .isEqualTo("importDefinitionDoesNotExist");
    assertThat(response.readEntity(DefinitionExceptionResponseDto.class).getDefinitions())
      .hasSize(1)
      .containsExactly(
        DefinitionExceptionItemDto.builder()
          .type(DefinitionType.PROCESS)
          .key(DEFINITION_KEY)
          .versions(Collections.singletonList(DEFINITION_VERSION))
          .tenantIds(Collections.singletonList("tenant2"))
          .build());
  }

  @Test
  public void importReport_partiallyMissingTenants() {
    // given a definition that exists on tenant 1
    // and a report that requires this definition for tenant1 and tenant2
    engineIntegrationExtension.createTenant("tenant1");
    engineIntegrationExtension.createTenant("tenant2");
    importAllEngineEntitiesFromScratch();

    createAndSaveDefinition(DefinitionType.PROCESS, "tenant1");
    dateFreezer().freezeDateAndReturn();
    final SingleProcessReportDefinitionRequestDto reportDefToImport = createSimpleProcessReportDefinition();
    final SingleProcessReportDefinitionExportDto exportedReportDto =
      new SingleProcessReportDefinitionExportDto(reportDefToImport);
    exportedReportDto.getData().setTenantIds(Lists.newArrayList("tenant1", "tenant2"));

    // when
    final EntityIdResponseDto importedId = importClient.importEntityAndReturnId(exportedReportDto);

    // then
    final SingleProcessReportDefinitionRequestDto importedReport =
      (SingleProcessReportDefinitionRequestDto) reportClient.getReportById(importedId.getId());

    assertImportedReport(importedReport, reportDefToImport, null);
  }

  @Test
  public void importReport_sharedDefinitionButSpecificReportTenant() {
    // given a definition that exists on the shared null tenant
    // and a report that requires this definition for tenant1
    engineIntegrationExtension.createTenant("tenant1");
    importAllEngineEntitiesFromScratch();

    createAndSaveDefinition(DefinitionType.PROCESS, null);
    final OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();
    exportedReportDto.getData().setTenantIds(Lists.newArrayList("tenant1"));

    // when
    final EntityIdResponseDto importedId = importClient.importEntityAndReturnId(exportedReportDto);

    // then
    final SingleProcessReportDefinitionRequestDto importedReport =
      (SingleProcessReportDefinitionRequestDto) reportClient.getReportById(importedId.getId());

    assertThat(importedReport.getOwner()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(importedReport.getLastModifier()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(importedReport.getCreated()).isEqualTo(now);
    assertThat(importedReport.getLastModified()).isEqualTo(now);
    assertThat(importedReport.getCollectionId()).isNull();
    assertThat(importedReport.getName()).isEqualTo(exportedReportDto.getName());
    assertThat(importedReport.getData())
      .usingRecursiveComparison()
      .ignoringFields(SingleReportDataDto.Fields.configuration)
      .isEqualTo(importedReport.getData());
    assertThat(importedReport.getData().getConfiguration())
      .usingRecursiveComparison()
      .ignoringFields(SingleReportConfigurationDto.Fields.xml)
      .isEqualTo(importedReport.getData().getConfiguration());
    assertThat(importedReport.getData().getConfiguration().getXml())
      .isEqualTo(DEFINITION_XML_STRING + "1");
  }

  @ParameterizedTest
  @MethodSource("getTestProcessReports")
  public void importReportIntoCollection(final SingleProcessReportDefinitionRequestDto reportDefToImport) {
    // given
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    final OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    final String collectionId = collectionClient.createNewCollectionWithScope(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      DefinitionType.PROCESS,
      DEFINITION_KEY,
      Collections.singletonList(null)
    );
    final SingleProcessReportDefinitionExportDto exportedReportDto = createExportDto(reportDefToImport);

    // when
    final EntityIdResponseDto importedId = importClient.importEntityIntoCollectionAndReturnId(
      collectionId,
      exportedReportDto
    );

    // then
    final SingleProcessReportDefinitionRequestDto importedReport =
      (SingleProcessReportDefinitionRequestDto) reportClient.getReportById(importedId.getId());

    assertThat(importedReport.getOwner()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(importedReport.getLastModifier()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(importedReport.getCreated()).isEqualTo(now);
    assertThat(importedReport.getLastModified()).isEqualTo(now);
    assertThat(importedReport.getCollectionId()).isEqualTo(collectionId);
    assertThat(importedReport.getName()).isEqualTo(reportDefToImport.getName());
    assertThat(importedReport.getData())
      .usingRecursiveComparison()
      .ignoringFields(SingleReportDataDto.Fields.configuration)
      .isEqualTo(importedReport.getData());
    assertThat(importedReport.getData().getConfiguration())
      .usingRecursiveComparison()
      .ignoringFields(SingleReportConfigurationDto.Fields.xml)
      .isEqualTo(importedReport.getData().getConfiguration());
    assertThat(importedReport.getData().getConfiguration().getXml())
      .isEqualTo(DEFINITION_XML_STRING + "1");
  }

  @Test
  public void importReportIntoCollection_collectionDoesNotExist() {
    // given
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();

    // when
    final Response response =
      importClient.importEntityIntoCollection("fakeCollection", exportedReportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void importReportIntoCollection_missingScope() {
    // given
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    final String collectionId = collectionClient.createNewCollection();
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();

    // when
    final Response response = importClient.importEntityIntoCollection(collectionId, exportedReportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
  }

  @Test
  public void importReportIntoCollection_multiDefinitionPartiallyMissingScope() {
    // given
    final String key1 = "key1";
    final String key2 = "key2";
    createAndSaveDefinition(key1, DefinitionType.PROCESS, null);
    createAndSaveDefinition(key2, DefinitionType.PROCESS, null);
    final String collectionId = collectionClient.createNewCollection();
    collectionClient.addScopeEntryToCollection(collectionId, new CollectionScopeEntryDto(DefinitionType.PROCESS, key1));

    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();
    exportedReportDto.getData().setDefinitions(List.of(
      new ReportDataDefinitionDto(key1), new ReportDataDefinitionDto(key2)
    ));

    // when
    final Response response = importClient.importEntityIntoCollection(collectionId, exportedReportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("getTestCombinableReports")
  public void importCombinedReport(final List<SingleProcessReportDefinitionRequestDto> combinableReports) {
    // given
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    dateFreezer().freezeDateAndReturn();

    final CombinedReportDefinitionRequestDto combinedReportDef = createCombinedReportDefinition(combinableReports);

    final Set<OptimizeEntityExportDto> reportsToImport = Sets.newHashSet(
      createExportDto(combinedReportDef),
      createExportDto(combinableReports.get(0)),
      createExportDto(combinableReports.get(1))
    );

    // when
    final List<EntityIdResponseDto> importedIds = importClient.importEntitiesAndReturnIds(reportsToImport);

    // then
    assertThat(importedIds).hasSize(3);
    Map<String, ReportDefinitionDto<?>> importedReports = Stream.of(
      reportClient.getReportById(importedIds.get(0).getId()),
      reportClient.getReportById(importedIds.get(1).getId()),
      reportClient.getReportById(importedIds.get(2).getId())
    ).collect(toMap(ReportDefinitionDto::getName, Function.identity()));

    final CombinedReportDefinitionRequestDto importedCombinedReport =
      (CombinedReportDefinitionRequestDto) importedReports.get(combinedReportDef.getName());
    final SingleProcessReportDefinitionRequestDto importedSingleReport1 =
      (SingleProcessReportDefinitionRequestDto) importedReports.get(combinableReports.get(0).getName());
    final SingleProcessReportDefinitionRequestDto importedSingleReport2 =
      (SingleProcessReportDefinitionRequestDto) importedReports.get(combinableReports.get(1).getName());

    assertImportedReport(
      importedSingleReport1,
      combinableReports.get(0),
      null
    );
    assertImportedReport(
      importedSingleReport2,
      combinableReports.get(1),
      null
    );

    assertThat(importedCombinedReport.getOwner()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(importedCombinedReport.getLastModifier()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(importedCombinedReport.getCreated()).isEqualTo(LocalDateUtil.getCurrentDateTime());
    assertThat(importedCombinedReport.getLastModified()).isEqualTo(LocalDateUtil.getCurrentDateTime());
    assertThat(importedCombinedReport.getCollectionId()).isNull();
    assertThat(importedCombinedReport.getName()).isEqualTo(combinedReportDef.getName());
    assertThat(importedCombinedReport.getData())
      .usingRecursiveComparison()
      .ignoringFields(CombinedReportDataDto.Fields.reports)
      .isEqualTo(combinedReportDef.getData());
    assertThat(importedCombinedReport.getData().getReports())
      .usingRecursiveFieldByFieldElementComparatorIgnoringFields(CombinedReportItemDto.Fields.id)
      .containsExactlyElementsOf(combinedReportDef.getData().getReports());
    assertThat(importedCombinedReport.getData().getReportIds())
      .containsExactlyInAnyOrder(importedSingleReport1.getId(), importedSingleReport2.getId());
  }

  @ParameterizedTest
  @MethodSource("getTestCombinableReports")
  public void importCombinedReportIntoCollection(final List<SingleProcessReportDefinitionRequestDto> combinableReports) {
    // given
    dateFreezer().freezeDateAndReturn();
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    final String collectionId = collectionClient.createNewCollectionWithScope(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      DefinitionType.PROCESS,
      DEFINITION_KEY,
      Collections.singletonList(null)
    );

    final CombinedReportDefinitionRequestDto combinedReportDef = createCombinedReportDefinition(combinableReports);

    final Set<OptimizeEntityExportDto> reportsToImport = Sets.newHashSet(
      createExportDto(combinedReportDef),
      createExportDto(combinableReports.get(0)),
      createExportDto(combinableReports.get(1))
    );

    // when
    final List<EntityIdResponseDto> importedIds = importClient.importEntitiesIntoCollectionAndReturnIds(
      collectionId,
      reportsToImport
    );

    // then
    assertThat(importedIds).hasSize(3);
    Map<String, ReportDefinitionDto<?>> importedReports = Stream.of(
      reportClient.getReportById(importedIds.get(0).getId()),
      reportClient.getReportById(importedIds.get(1).getId()),
      reportClient.getReportById(importedIds.get(2).getId())
    ).collect(toMap(ReportDefinitionDto::getName, Function.identity()));

    final CombinedReportDefinitionRequestDto importedCombinedReport =
      (CombinedReportDefinitionRequestDto) importedReports.get(combinedReportDef.getName());
    final SingleProcessReportDefinitionRequestDto importedSingleReport1 =
      (SingleProcessReportDefinitionRequestDto) importedReports.get(combinableReports.get(0).getName());
    final SingleProcessReportDefinitionRequestDto importedSingleReport2 =
      (SingleProcessReportDefinitionRequestDto) importedReports.get(combinableReports.get(1).getName());

    assertImportedReport(
      importedSingleReport1,
      combinableReports.get(0),
      collectionId
    );
    assertImportedReport(
      importedSingleReport2,
      combinableReports.get(1),
      collectionId
    );

    assertThat(importedCombinedReport.getOwner()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(importedCombinedReport.getLastModifier()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(importedCombinedReport.getCreated()).isEqualTo(LocalDateUtil.getCurrentDateTime());
    assertThat(importedCombinedReport.getLastModified()).isEqualTo(LocalDateUtil.getCurrentDateTime());
    assertThat(importedCombinedReport.getCollectionId()).isEqualTo(collectionId);
    assertThat(importedCombinedReport.getName()).isEqualTo(combinedReportDef.getName());
    assertThat(importedCombinedReport.getData())
      .usingRecursiveComparison()
      .ignoringFields(CombinedReportDataDto.Fields.reports)
      .isEqualTo(combinedReportDef.getData());
    assertThat(importedCombinedReport.getData().getReports())
      .usingRecursiveFieldByFieldElementComparatorIgnoringFields(CombinedReportItemDto.Fields.id)
      .containsExactlyElementsOf(combinedReportDef.getData().getReports());
    assertThat(importedCombinedReport.getData().getReportIds())
      .containsExactlyInAnyOrder(importedSingleReport1.getId(), importedSingleReport2.getId());
  }

  @Test
  public void importIncompleteCombinedReport_throwsInvalidImportFileException() {
    // given an import file that includes a combined report but not the single report within the combined report
    final CombinedProcessReportDefinitionExportDto combinedReport = createSimpleCombinedExportDto();

    // when
    final Response response = importClient.importEntity(combinedReport);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(ErrorResponseDto.class).getErrorCode()).isEqualTo("importFileInvalid");
  }

}
