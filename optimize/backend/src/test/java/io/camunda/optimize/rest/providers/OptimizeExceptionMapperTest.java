/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.rest.DefinitionExceptionItemDto;
import io.camunda.optimize.dto.optimize.rest.DefinitionExceptionResponseDto;
import io.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import io.camunda.optimize.dto.optimize.rest.ImportIndexMismatchDto;
import io.camunda.optimize.dto.optimize.rest.ImportedIndexMismatchResponseDto;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.exceptions.OptimizeImportDefinitionDoesNotExistException;
import io.camunda.optimize.service.exceptions.OptimizeImportFileInvalidException;
import io.camunda.optimize.service.exceptions.OptimizeImportIncorrectIndexVersionException;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    when(localizationService.getDefaultLocaleMessageForApiErrorCode(
            OptimizeValidationException.ERROR_CODE))
        .thenReturn("The server was unable to process the request.");

    // when
    final var response =
        underTest.handleValidationException(
            new OptimizeValidationException(
                "Collection names cannot be greater than 3 characters"));

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    // literal on purpose: the code is API contract, keyed on by apiErrors.badRequestError in the UI
    assertThat(response.getBody().getErrorCode()).isEqualTo("badRequestError");
    assertThat(response.getBody().getDetailedMessage())
        .isEqualTo("Collection names cannot be greater than 3 characters");
  }

  /**
   * Only the subclasses whose handler returns a richer body are listed. For the name and
   * description subclasses the dedicated handler and the new superclass handler produce an
   * identical response, so routing between them is unobservable and nothing is left to assert.
   */
  private static Stream<Arguments> subclassesWhoseHandlerReturnsMoreThanAPlainError() {
    return Stream.of(
        Arguments.of(
            new OptimizeImportDefinitionDoesNotExistException(
                "missing definitions",
                Set.of(
                    new DefinitionExceptionItemDto(
                        DefinitionType.PROCESS, "key", List.of("1"), List.of("tenant")))),
            DefinitionExceptionResponseDto.class),
        Arguments.of(
            new OptimizeImportIncorrectIndexVersionException(
                "index mismatch", Set.of(new ImportIndexMismatchDto("index", 1, 2))),
            ImportedIndexMismatchResponseDto.class));
  }

  @ParameterizedTest
  @MethodSource("subclassesWhoseHandlerReturnsMoreThanAPlainError")
  void shouldNotDegradeSubclassPayloadsToAPlainError(
      final OptimizeValidationException exception, final Class<? extends ErrorResponseDto> expected)
      throws Exception {
    // given
    when(localizationService.getDefaultLocaleMessageForApiErrorCode(anyString()))
        .thenReturn("localized message");

    // when the exception goes through whichever handler Spring resolves for it
    final Method handler = resolver.resolveMethod(exception);
    final var response = (ResponseEntity<?>) handler.invoke(underTest, exception);

    // then the caller still gets the body carrying the offending entities
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isInstanceOf(expected);
  }

  @Test
  void shouldRejectValidationSubclassesThatHaveNoHandlerOfTheirOwn() throws Exception {
    // given a subclass no dedicated handler covers, so it used to reach the catch-all as a 500
    when(localizationService.getDefaultLocaleMessageForApiErrorCode(anyString()))
        .thenReturn("localized message");
    final var exception = new OptimizeImportFileInvalidException("file invalid");

    // when
    final Method handler = resolver.resolveMethod(exception);
    final var response = (ResponseEntity<?>) handler.invoke(underTest, exception);

    // then it is rejected, and keeps its own error code rather than the superclass one
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(((ErrorResponseDto) response.getBody()).getErrorCode())
        .isEqualTo("importFileInvalid");
  }
}
