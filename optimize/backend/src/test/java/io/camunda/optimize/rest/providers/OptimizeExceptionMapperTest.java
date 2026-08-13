/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.exceptions.OptimizeImportDescriptionNotValidException;
import io.camunda.optimize.service.exceptions.OptimizeImportNameNotValidException;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

@ExtendWith(MockitoExtension.class)
public class OptimizeExceptionMapperTest {

  private final ExceptionHandlerMethodResolver resolver =
      new ExceptionHandlerMethodResolver(OptimizeExceptionMapper.class);

  @Mock private LocalizationService localizationService;

  @InjectMocks private OptimizeExceptionMapper underTest;

  @Test
  void shouldRejectRatherThanReportAnInternalErrorForAValidationFailure() {
    // given
    when(localizationService.getDefaultLocaleMessageForApiErrorCode("badRequestError"))
        .thenReturn("The server was unable to process the request.");

    // when
    final var response =
        underTest.handleValidationException(
            new OptimizeValidationException(
                "Collection names cannot be greater than 3 characters"));

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().getErrorCode()).isEqualTo("badRequestError");
    assertThat(response.getBody().getDetailedMessage())
        .isEqualTo("Collection names cannot be greater than 3 characters");
  }

  @Test
  void shouldStillRouteValidationSubclassesToTheirOwnHandler() {
    // then the added superclass handler does not swallow the subclasses carrying the entity ids
    assertThat(resolver.resolveMethod(new OptimizeImportNameNotValidException(Set.of("id"))))
        .extracting(Method::getName)
        .isEqualTo("handleImportNameNotValidException");
    assertThat(resolver.resolveMethod(new OptimizeImportDescriptionNotValidException(Set.of("id"))))
        .extracting(Method::getName)
        .isEqualTo("handleReportEvaluationException");
  }
}
