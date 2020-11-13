/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.entities.report;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.rest.DefinitionExceptionItemDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionExceptionResponseDto;
import org.camunda.optimize.dto.optimize.rest.export.SingleProcessReportDefinitionExportDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

public class ReportImportAuthorizationIT extends AbstractReportExportImportIT {

  @Test
  public void importProcessReport_asSuperuser() {
    // given
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();

    // when
    final Response response = importClient.importProcessReport(exportedReportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void importProcessReport_asSuperuser_withoutDefinitionAuth() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(KERMIT_USER);
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();

    // when
    final Response response = importClient.importProcessReportAsUser(KERMIT_USER, KERMIT_USER, exportedReportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    assertThat(response.readEntity(DefinitionExceptionResponseDto.class).getErrorCode())
      .isEqualTo("importDefinitionForbidden");

    assertThat(response.readEntity(DefinitionExceptionResponseDto.class).getDefinitions())
      .hasSize(1)
      .containsExactly(
        DefinitionExceptionItemDto.builder()
          .type(DefinitionType.PROCESS)
          .key(DEFINITION_KEY)
          .tenantIds(Collections.singletonList(null))
          .build());
  }

  @Test
  public void importProcessReport_asSuperuser_withoutTenantAuth() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(KERMIT_USER);
    engineIntegrationExtension.createTenant("tenant1");
    createAndSaveDefinition(DefinitionType.PROCESS, "tenant1");
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();
    exportedReportDto.getData().setTenantIds(Lists.newArrayList("tenant1"));

    // when
    final Response response = importClient.importProcessReportAsUser(KERMIT_USER, KERMIT_USER, exportedReportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    assertThat(response.readEntity(DefinitionExceptionResponseDto.class).getErrorCode())
      .isEqualTo("importDefinitionForbidden");
    assertThat(response.readEntity(DefinitionExceptionResponseDto.class).getDefinitions())
      .hasSize(1)
      .containsExactly(
        DefinitionExceptionItemDto.builder()
          .type(DefinitionType.PROCESS)
          .key(DEFINITION_KEY)
          .tenantIds(Collections.singletonList("tenant1"))
          .build());
  }

  @Test
  public void importProcessReport_asNonSuperuser() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();

    // when
    final Response response = importClient.importProcessReportAsUser(KERMIT_USER, KERMIT_USER, exportedReportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void importProcessReportIntoCollection_asSuperuser() {
    // given
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    final String collectionId = collectionClient.createNewCollectionWithScope(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      DefinitionType.PROCESS,
      DEFINITION_KEY,
      Collections.singletonList(null)
    );
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();

    // when
    final Response response = importClient.importProcessReportIntoCollection(collectionId, exportedReportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void importProcessReportIntoCollection_asSuperuser_withoutDefinitionAuth() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(KERMIT_USER);
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    final String collectionId = collectionClient.createNewCollectionWithScope(
      KERMIT_USER,
      KERMIT_USER,
      DefinitionType.PROCESS,
      DEFINITION_KEY,
      Collections.singletonList(null)
    );
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();

    // when
    final Response response = importClient.importProcessReportIntoCollectionAsUser(
      KERMIT_USER,
      KERMIT_USER,
      collectionId,
      exportedReportDto
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    assertThat(response.readEntity(DefinitionExceptionResponseDto.class).getErrorCode())
      .isEqualTo("importDefinitionForbidden");
    assertThat(response.readEntity(DefinitionExceptionResponseDto.class).getDefinitions())
      .hasSize(1)
      .containsExactly(
        DefinitionExceptionItemDto.builder()
          .type(DefinitionType.PROCESS)
          .key(DEFINITION_KEY)
          .tenantIds(Collections.singletonList(null))
          .build());
  }

  @Test
  public void importProcessReportIntoCollection_asNonSuperuser() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    createAndSaveDefinition(DefinitionType.PROCESS, null);
    final String collectionId = collectionClient.createNewCollectionWithScope(
      KERMIT_USER,
      KERMIT_USER,
      DefinitionType.PROCESS,
      DEFINITION_KEY,
      Collections.singletonList(null)
    );
    final SingleProcessReportDefinitionExportDto exportedReportDto = createSimpleProcessExportDto();

    // when
    final Response response = importClient.importProcessReportIntoCollectionAsUser(
      KERMIT_USER,
      KERMIT_USER,
      collectionId,
      exportedReportDto
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

}
