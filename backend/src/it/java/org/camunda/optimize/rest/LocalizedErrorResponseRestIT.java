/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.providers.GenericExceptionMapper.NOT_FOUND_ERROR_CODE;

public class LocalizedErrorResponseRestIT extends AbstractIT {

  @Test
  public void fallbackLocaleMessageIsResolved() {
    // given

    // when
    final ErrorResponseDto errorResponseDto = executeInvalidPathRequest();

    // then
    assertThat(errorResponseDto)
      .usingRecursiveComparison()
      .ignoringExpectedNullFields()
      .isEqualTo(new ErrorResponseDto(
        NOT_FOUND_ERROR_CODE, "The server could not find the requested resource.", null, null
      ));
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
      .isEqualTo(new ErrorResponseDto(
        NOT_FOUND_ERROR_CODE, "Der Server konnte die angeforderte Seite oder Datei nicht finden.", null, null
      ));
  }

  private ErrorResponseDto executeInvalidPathRequest() {
    return embeddedOptimizeExtension.getRequestExecutor()
      .buildGenericRequest(HttpMethod.GET, "/api/doesNotExist", null)
      .execute(ErrorResponseDto.class, Response.Status.NOT_FOUND.getStatusCode());
  }

}
