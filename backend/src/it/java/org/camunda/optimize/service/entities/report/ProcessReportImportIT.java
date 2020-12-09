/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.entities.report;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionExceptionItemDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionExceptionResponseDto;
import org.camunda.optimize.dto.optimize.rest.ImportIndexMismatchDto;
import org.camunda.optimize.dto.optimize.rest.ImportedIndexMismatchResponseDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FIRSTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_LASTNAME;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;

public class ProcessReportImportIT extends AbstractReportExportImportIT {

  @ParameterizedTest
  @MethodSource("getTestProcessReports")
  public void importReport(final SingleProcessReportDefinitionRequestDto reportDefToImport) {
    // given
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    final OffsetDateTime now = dateFreezer().freezeDateAndReturn();

    // when
    final Response response = importClient.importReport(createExportDto(reportDefToImport));

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final List<IdResponseDto> importedIds = response.readEntity(new GenericType<List<IdResponseDto>>(){});
    assertThat(importedIds).hasSize(1);
    final SingleProcessReportDefinitionRequestDto importedReport =
      (SingleProcessReportDefinitionRequestDto) reportClient.getReportById(importedIds.get(0).getId());

    assertThat(importedReport.getOwner()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(importedReport.getLastModifier()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(importedReport.getCreated()).isEqualTo(now);
    assertThat(importedReport.getLastModified()).isEqualTo(now);
    assertThat(importedReport.getCollectionId()).isNull();
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
  public void importReport_incorrectIndexVersion() {
    // given a report with report index version different from the current version
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();
    exportedReportDto.setSourceIndexVersion(SingleProcessReportIndex.VERSION + 1);

    // when
    final Response response = importClient.importReport(exportedReportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(ImportedIndexMismatchResponseDto.class).getErrorCode())
      .isEqualTo("importIndexVersionMismatch");
    assertThat(response.readEntity(ImportedIndexMismatchResponseDto.class).getMismatchingIndices())
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
    final Response response = importClient.importReport(exportedReportDto);

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
          .tenantIds(Collections.singletonList(null))
          .build());
  }

  @Test
  public void importReport_missingVersion() {
    // given a definition that only exists with version 1 and a report that requires version 5 of this definition
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();
    exportedReportDto.getData().setProcessDefinitionVersion("5");

    // when
    final Response response = importClient.importReport(exportedReportDto);

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
          .versions(Collections.singletonList("5"))
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
    final Response response = importClient.importReport(exportedReportDto);

    // then all non version related data is accurate
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final List<IdResponseDto> importedIds = response.readEntity(new GenericType<List<IdResponseDto>>(){});
    assertThat(importedIds).hasSize(1);
    final SingleProcessReportDefinitionRequestDto importedReport =
      (SingleProcessReportDefinitionRequestDto) reportClient.getReportById(importedIds.get(0).getId());

    assertThat(importedReport.getOwner()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(importedReport.getLastModifier()).isEqualTo(DEFAULT_FIRSTNAME + " " + DEFAULT_LASTNAME);
    assertThat(importedReport.getCreated()).isEqualTo(now);
    assertThat(importedReport.getLastModified()).isEqualTo(now);
    assertThat(importedReport.getCollectionId()).isNull();
    assertThat(importedReport.getName()).isEqualTo(reportDefinitionToImport.getName());
    assertThat(importedReport.getData())
      .usingRecursiveComparison()
      .ignoringFields(ProcessReportDataDto.Fields.processDefinitionVersions)
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
    final Response response = importClient.importReport(exportedReportDto);

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
    final OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();
    exportedReportDto.getData().setTenantIds(Lists.newArrayList("tenant1", "tenant2"));

    // when
    final Response response = importClient.importReport(exportedReportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final List<IdResponseDto> importedIds = response.readEntity(new GenericType<List<IdResponseDto>>(){});
    assertThat(importedIds).hasSize(1);
    final SingleProcessReportDefinitionRequestDto importedReport =
      (SingleProcessReportDefinitionRequestDto) reportClient.getReportById(importedIds.get(0).getId());

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
    final Response response = importClient.importReport(exportedReportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final List<IdResponseDto> importedIds = response.readEntity(new GenericType<List<IdResponseDto>>(){});
    assertThat(importedIds).hasSize(1);
    final SingleProcessReportDefinitionRequestDto importedReport =
      (SingleProcessReportDefinitionRequestDto) reportClient.getReportById(importedIds.get(0).getId());

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
    final Response response = importClient.importReportIntoCollection(
      collectionId,
      exportedReportDto
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    final List<IdResponseDto> importedIds = response.readEntity(new GenericType<List<IdResponseDto>>(){});
    assertThat(importedIds).hasSize(1);
    final SingleProcessReportDefinitionRequestDto importedReport =
      (SingleProcessReportDefinitionRequestDto) reportClient.getReportById(importedIds.get(0).getId());

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
  public void importReportIntoNonExistentCollection() {
    // given
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();

    // when
    final Response response =
      importClient.importReportIntoCollection("fakeCollection", exportedReportDto);

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
    final Response response = importClient.importReportIntoCollection(
      collectionId,
      exportedReportDto
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
  }

}
