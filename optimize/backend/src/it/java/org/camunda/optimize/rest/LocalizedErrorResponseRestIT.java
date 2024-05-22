/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static org.camunda.optimize.rest.providers.GenericExceptionMapper.NOT_FOUND_ERROR_CODE;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.Response;
import org.camunda.optimize.AbstractPlatformIT;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class LocalizedErrorResponseRestIT extends AbstractPlatformIT {

  @Test
  public void fallbackLocaleMessageIsResolved() {
    // given

    // when
    final ErrorResponseDto errorResponseDto = executeInvalidPathRequest();

    // then
    assertThat(errorResponseDto)
        .usingRecursiveComparison()
        .ignoringExpectedNullFields()
        .isEqualTo(
            new ErrorResponseDto(
                NOT_FOUND_ERROR_CODE,
                "The server could not find the requested resource.",
                null,
                null));
  }

  @Test
  public void customFallbackLocaleMessageIsResolved() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setFallbackLocale("de");

    // when
    final ErrorResponseDto errorResponseDto = executeInvalidPathRequest();

    // then
    assertThat(errorResponseDto)
        .usingRecursiveComparison()
        .ignoringExpectedNullFields()
        .isEqualTo(
            new ErrorResponseDto(
                NOT_FOUND_ERROR_CODE,
                "Der Server konnte die angeforderte Seite oder Datei nicht finden.",
                null,
                null));
  }

  private ErrorResponseDto executeInvalidPathRequest() {
    return embeddedOptimizeExtension
        .getRequestExecutor()
        .buildGenericRequest(HttpMethod.GET, "/api/doesNotExist", null)
        .execute(ErrorResponseDto.class, Response.Status.NOT_FOUND.getStatusCode());
  }
}
