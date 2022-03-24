/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.providers;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.service.LocalizationService;
import org.elasticsearch.ElasticsearchStatusException;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
@Slf4j
public class ElasticsearchStatusExceptionMapper implements ExceptionMapper<ElasticsearchStatusException> {
  private static final String ELASTICSEARCH_ERROR_CODE = "elasticsearchError";

  private final LocalizationService localizationService;

  public ElasticsearchStatusExceptionMapper(@Context final LocalizationService localizationService) {
    this.localizationService = localizationService;
  }

  @Override
  public Response toResponse(final ElasticsearchStatusException esStatusException) {
    log.error("Mapping ElasticsearchStatusException", esStatusException);

    return Response
      .status(Response.Status.INTERNAL_SERVER_ERROR)
      .type(MediaType.APPLICATION_JSON_TYPE)
      .entity(mapToEvaluationErrorResponseDto(esStatusException))
      .build();
  }

  private ErrorResponseDto mapToEvaluationErrorResponseDto(final ElasticsearchStatusException esStatusException) {
    final String errorMessage = localizationService.getDefaultLocaleMessageForApiErrorCode(ELASTICSEARCH_ERROR_CODE);
    final String detailedErrorMessage = esStatusException.getMessage();

    return new ErrorResponseDto(
      ELASTICSEARCH_ERROR_CODE,
      errorMessage,
      detailedErrorMessage
    );
  }

}
