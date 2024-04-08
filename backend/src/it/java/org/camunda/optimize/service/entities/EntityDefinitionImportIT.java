/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class EntityDefinitionImportIT extends AbstractExportImportEntityDefinitionIT {

  @Test
  public void importEmptyFile_throwsInvalidImportFileException() {
    // when importing an empty entity
    final Response response = importObject(Entity.entity("", MediaType.APPLICATION_JSON_TYPE));

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(ErrorResponseDto.class).getErrorCode())
        .isEqualTo("importFileInvalid");
  }

  @Test
  public void importNonJsonFile_throwsInvalidImportFileException() {
    // when importing something that is not valid json
    final Response response =
        importObject(Entity.entity("I am not valid json", MediaType.APPLICATION_JSON_TYPE));

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(ErrorResponseDto.class).getErrorCode())
        .isEqualTo("importFileInvalid");
  }

  @SneakyThrows
  @Test
  public void importIncorrectJson_throwsInvalidImportFileException() {
    // given a dto that is not an importable entity
    final UserDto incorrectDto = new UserDto("someId", "someName");

    // when
    final Response response =
        importObject(
            Entity.entity(
                embeddedOptimizeExtension.getObjectMapper().writeValueAsString(incorrectDto),
                MediaType.APPLICATION_JSON_TYPE));

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(ErrorResponseDto.class).getErrorCode())
        .isEqualTo("importFileInvalid");
  }

  @Test
  public void importInvalidEntity_throwsInvalidImportFileException() {
    // given an import entity with a non-nullable field set to null
    final SingleProcessReportDefinitionExportDto exportDto = createSimpleProcessExportDto();
    exportDto.setId(null);

    // when
    final Response response = importClient.importEntity(exportDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(ErrorResponseDto.class).getErrorCode())
        .isEqualTo("importFileInvalid");
  }

  private Response importObject(final Entity<?> entityToImport) {
    return embeddedOptimizeExtension
        .getRequestExecutor()
        .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
        .buildImportEntityRequest(entityToImport)
        .execute();
  }
}
