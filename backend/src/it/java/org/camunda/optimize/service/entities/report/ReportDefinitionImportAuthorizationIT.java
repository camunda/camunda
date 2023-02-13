/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.entities.report;

import com.google.common.collect.Sets;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.rest.DefinitionExceptionItemDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionExceptionResponseDto;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.service.entities.AbstractExportImportEntityDefinitionIT;
import org.camunda.optimize.util.SuperUserType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public class ReportDefinitionImportAuthorizationIT extends AbstractExportImportEntityDefinitionIT {

  @ParameterizedTest
  @MethodSource("reportAndAuthType")
  public void importReport_asSuperuser(final ReportType reportType, final SuperUserType superUserType) {
    // given
    createAndSaveDefinition(reportType.toDefinitionType(), null);

    // when
    final Response response;
    if (superUserType == SuperUserType.USER) {
      response = importClient.importEntity(createSimpleExportDto(reportType));
    } else {
      setAuthorizedSuperGroup();
      response = importClient.importEntityAsUser(KERMIT_USER, KERMIT_USER, createSimpleExportDto(reportType));
    }

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("reportAndAuthType")
  public void importReport_asNonSuperuser(final ReportType reportType) {
    // given
    createAndSaveDefinition(reportType.toDefinitionType(), null);
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    if (reportType.equals(ReportType.PROCESS)) {
      authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);
    } else if (reportType.equals(ReportType.DECISION)) {
      authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_DECISION_DEFINITION);
    }

    // when
    final Response response = importClient.importEntityAsUser(
      KERMIT_USER,
      KERMIT_USER,
      createSimpleExportDto(reportType)
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void importReport_withoutDefinitionAuth(final ReportType reportType) {
    // given two reports for a forbidden definition
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    createAndSaveDefinition(reportType.toDefinitionType(), null);
    final OptimizeEntityExportDto report1 = createSimpleExportDto(reportType);
    final OptimizeEntityExportDto report2 = createSimpleExportDto(reportType);
    report2.setId("some other Id");

    // when
    final Response response = importClient.importEntitiesAsUser(
      KERMIT_USER,
      KERMIT_USER,
      Sets.newHashSet(report1, report2)
    );

    // then a definition forbidden exception is thrown which contains the forbidden definition once
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    assertThat(response.readEntity(DefinitionExceptionResponseDto.class).getErrorCode())
      .isEqualTo("importDefinitionForbidden");

    assertThat(response.readEntity(DefinitionExceptionResponseDto.class).getDefinitions())
      .hasSize(1)
      .containsExactly(
        DefinitionExceptionItemDto.builder()
          .type(reportType.toDefinitionType())
          .key(DEFINITION_KEY)
          .tenantIds(Collections.singletonList(null))
          .build());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void importReport_withoutTenantAuth(final ReportType reportType) {
    // given two reports with forbidden tenants
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    engineIntegrationExtension.createTenant("tenant1");
    createAndSaveDefinition(reportType.toDefinitionType(), "tenant1");
    final OptimizeEntityExportDto report1 = createSimpleExportDtoWithTenants(
      reportType,
      Collections.singletonList("tenant1")
    );
    final OptimizeEntityExportDto report2 = createSimpleExportDtoWithTenants(
      reportType,
      Collections.singletonList("tenant1")
    );
    report2.setId("some other ID");

    // when
    final Response response = importClient.importEntitiesAsUser(
      KERMIT_USER,
      KERMIT_USER,
      Sets.newHashSet(report1, report2)
    );

    // then a definition forbidden exception is thrown which contains the forbidden definition once
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
    assertThat(response.readEntity(DefinitionExceptionResponseDto.class).getErrorCode())
      .isEqualTo("importDefinitionForbidden");
    assertThat(response.readEntity(DefinitionExceptionResponseDto.class).getDefinitions())
      .hasSize(1)
      .containsExactly(
        DefinitionExceptionItemDto.builder()
          .type(reportType.toDefinitionType())
          .key(DEFINITION_KEY)
          .tenantIds(Collections.singletonList("tenant1"))
          .build());
  }

  @ParameterizedTest
  @EnumSource(ReportType.class)
  public void importReportIntoCollection(final ReportType reportType) {
    // given
    final String collectionId = collectionClient.createNewCollectionWithScope(
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD,
      reportType.toDefinitionType(),
      DEFINITION_KEY,
      Collections.singletonList(null)
    );
    createAndSaveDefinition(reportType.toDefinitionType(), null);

    // when
    final Response response = importClient.importEntityIntoCollection(
      collectionId,
      createSimpleExportDto(reportType)
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

}
