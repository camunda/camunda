/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.entities.report;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.EntityIdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionExceptionItemDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionExceptionResponseDto;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.dto.optimize.rest.ImportIndexMismatchDto;
import org.camunda.optimize.dto.optimize.rest.ImportedIndexMismatchResponseDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleDecisionReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import org.camunda.optimize.service.entities.AbstractExportImportEntityDefinitionIT;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_FIRSTNAME;
import static org.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_LASTNAME;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;

public class DecisionReportDefinitionImportIT extends AbstractExportImportEntityDefinitionIT {

  @ParameterizedTest
  @MethodSource("getTestDecisionReports")
  public void importReport(final SingleDecisionReportDefinitionRequestDto reportDefToImport) {
    // given
    createAndSaveDefinition(DefinitionType.DECISION, null);
    dateFreezer().freezeDateAndReturn();

    // when
    final EntityIdResponseDto importedId = importClient.importEntityAndReturnId(createExportDto(reportDefToImport));

    // then
    final SingleDecisionReportDefinitionRequestDto importedReport =
      (SingleDecisionReportDefinitionRequestDto) reportClient.getReportById(importedId.getId());

    assertImportedReport(importedReport, reportDefToImport, null);
  }

  @Test
  public void importReportWithInvalidDescription() {
    // given
    final SingleDecisionReportDefinitionExportDto simpleDecisionExportDto = createSimpleDecisionExportDto();
    simpleDecisionExportDto.setDescription("");

    // when
    final Response response = importClient.importEntity(simpleDecisionExportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    final ErrorResponseDto invalidDescriptionResponseDto = response.readEntity(ErrorResponseDto.class);
    assertThat(invalidDescriptionResponseDto.getErrorCode()).isEqualTo("importDescriptionInvalid");
  }

  @Test
  public void importReport_incorrectIndexVersion() {
    // given a report with report index version different from the current version
    final SingleDecisionReportDefinitionExportDto exportedReportDto = createSimpleDecisionExportDto();
    exportedReportDto.setSourceIndexVersion(SingleDecisionReportIndex.VERSION + 1);

    // when
    final Response response = importClient.importEntity(exportedReportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(ImportedIndexMismatchResponseDto.class).getErrorCode())
      .isEqualTo("importIndexVersionMismatch");
    assertThat(response.readEntity(ImportedIndexMismatchResponseDto.class).getMismatchingIndices())
      .hasSize(1)
      .containsExactly(
        ImportIndexMismatchDto.builder()
          .indexName(embeddedOptimizeExtension.getIndexNameService()
                       .getOptimizeIndexNameWithVersion(new SingleDecisionReportIndex()))
          .sourceIndexVersion(SingleDecisionReportIndex.VERSION + 1)
          .targetIndexVersion(SingleDecisionReportIndex.VERSION)
          .build());
  }

  @Test
  public void importReport_missingDefinition() {
    // given a report for a definition that doesn't exist
    final SingleDecisionReportDefinitionExportDto exportedReportDto = createSimpleDecisionExportDto();

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
          .type(DefinitionType.DECISION)
          .key(DEFINITION_KEY)
          .versions(Collections.singletonList(DEFINITION_VERSION))
          .tenantIds(Collections.singletonList(null))
          .build());
  }

  @Test
  public void importReport_missingVersion() {
    // given a definition that only exists with version 1 and a report that requires version 5 of this definition
    createAndSaveDefinition(DefinitionType.DECISION, null);
    final SingleDecisionReportDefinitionExportDto exportedReportDto = createSimpleDecisionExportDto();
    exportedReportDto.getData().setDecisionDefinitionVersion("5");

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
          .type(DefinitionType.DECISION)
          .key(DEFINITION_KEY)
          .versions(Collections.singletonList("5"))
          .tenantIds(Collections.singletonList(null))
          .build());
  }

  @Test
  public void importReport_partiallyMissingVersions() {
    // given a definition that only exists with version 1 and 3
    // and a report that requires version 1,2,3,5 of this definition
    createAndSaveDefinition(DefinitionType.DECISION, null, "1");
    createAndSaveDefinition(DefinitionType.DECISION, null, "3");
    final OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    final SingleDecisionReportDefinitionRequestDto reportDefinitionToImport = createSimpleDecisionReportDefinition();
    final SingleDecisionReportDefinitionExportDto exportedReportDto = createSimpleDecisionExportDto();
    exportedReportDto.getData().setDecisionDefinitionVersions(Lists.newArrayList("1", "2", "3", "5"));
    exportedReportDto.getData().getConfiguration().setXml("oldXml");

    // when
    final EntityIdResponseDto importedId = importClient.importEntityAndReturnId(exportedReportDto);

    // then all non version related data is accurate
    final SingleDecisionReportDefinitionRequestDto importedReport =
      (SingleDecisionReportDefinitionRequestDto) reportClient.getReportById(importedId.getId());

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

    createAndSaveDefinition(DefinitionType.DECISION, "tenant1");
    final SingleDecisionReportDefinitionExportDto exportedReportDto = createSimpleDecisionExportDto();
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
          .type(DefinitionType.DECISION)
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

    createAndSaveDefinition(DefinitionType.DECISION, "tenant1");
    dateFreezer().freezeDateAndReturn();
    final SingleDecisionReportDefinitionRequestDto reportDefToImport = createSimpleDecisionReportDefinition();
    final SingleDecisionReportDefinitionExportDto exportedReportDto =
      new SingleDecisionReportDefinitionExportDto(reportDefToImport);
    exportedReportDto.getData().setTenantIds(Lists.newArrayList("tenant1", "tenant2"));

    // when
    final EntityIdResponseDto importedId = importClient.importEntityAndReturnId(exportedReportDto);

    // then
    final SingleDecisionReportDefinitionRequestDto importedReport =
      (SingleDecisionReportDefinitionRequestDto) reportClient.getReportById(importedId.getId());

    assertImportedReport(importedReport, reportDefToImport, null);
  }

  @Test
  public void importReport_sharedDefinitionButSpecificReportTenant() {
    // given a definition that exists on the shared null tenant
    // and a report that requires this definition for tenant1
    engineIntegrationExtension.createTenant("tenant1");
    importAllEngineEntitiesFromScratch();

    createAndSaveDefinition(DefinitionType.DECISION, null);
    dateFreezer().freezeDateAndReturn();
    final SingleDecisionReportDefinitionRequestDto reportDefToImport = createSimpleDecisionReportDefinition();
    final SingleDecisionReportDefinitionExportDto exportedReportDto =
      new SingleDecisionReportDefinitionExportDto(reportDefToImport);
    exportedReportDto.getData().setTenantIds(Lists.newArrayList("tenant1"));

    // when
    final EntityIdResponseDto importedId = importClient.importEntityAndReturnId(exportedReportDto);

    // then
    final SingleDecisionReportDefinitionRequestDto importedReport =
      (SingleDecisionReportDefinitionRequestDto) reportClient.getReportById(importedId.getId());

    assertImportedReport(importedReport, reportDefToImport, null);
  }

  @ParameterizedTest
  @MethodSource("getTestDecisionReports")
  public void importReportIntoCollection(final SingleDecisionReportDefinitionRequestDto reportDefToImport) {
    // given
    createAndSaveDefinition(DefinitionType.DECISION, null);
    dateFreezer().freezeDateAndReturn();
    final String collectionId = collectionClient.createNewCollectionWithScope(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      DefinitionType.DECISION,
      DEFINITION_KEY,
      Collections.singletonList(null)
    );
    final SingleDecisionReportDefinitionExportDto exportedReportDto = createExportDto(reportDefToImport);

    // when
    final EntityIdResponseDto importedId = importClient.importEntityIntoCollectionAndReturnId(
      collectionId,
      exportedReportDto
    );

    // then
    final SingleDecisionReportDefinitionRequestDto importedReport =
      (SingleDecisionReportDefinitionRequestDto) reportClient.getReportById(importedId.getId());

    assertImportedReport(importedReport, reportDefToImport, collectionId);
  }

  @Test
  public void importReportIntoCollection_collectionDoesNotExist() {
    // given
    createAndSaveDefinition(DefinitionType.DECISION, null);
    final SingleDecisionReportDefinitionExportDto exportedReportDto = createSimpleDecisionExportDto();

    // when
    final Response response =
      importClient.importEntityIntoCollection("fakeCollection", exportedReportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void importReportIntoCollection_missingScope() {
    // given
    createAndSaveDefinition(DefinitionType.DECISION, null);
    final String collectionId = collectionClient.createNewCollection();
    final SingleDecisionReportDefinitionExportDto exportedReportDto = createSimpleDecisionExportDto();

    // when
    final Response response = importClient.importEntityIntoCollection(
      collectionId,
      exportedReportDto
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.CONFLICT.getStatusCode());
  }
}
