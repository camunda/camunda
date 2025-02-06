/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.rest.AlertEmailValidationResponseDto;
import io.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import io.camunda.optimize.rest.providers.OptimizeExceptionMapper;
import io.camunda.optimize.service.exceptions.OptimizeAlertEmailValidationException;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class OptimizeExceptionMapperTest {

  @Test
  public void exceptionContainsListOfInvalidEmails() {
    // given
    final Set<String> invalidEmails = Set.of("invalid@email.com", "another@bademail.com");
    final OptimizeAlertEmailValidationException emailValidationException =
        new OptimizeAlertEmailValidationException(invalidEmails);
    final OptimizeExceptionMapper underTest = new OptimizeExceptionMapper();

    // when
    final ResponseEntity<AlertEmailValidationResponseDto> response =
        underTest.handleOptimizeAlertEmailValidationException(emailValidationException);
    final Map<String, Object> mappedResponse =
        new ObjectMapper().convertValue(response.getBody(), new TypeReference<>() {});

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(mappedResponse.get(ErrorResponseDto.Fields.errorCode))
        .asString()
        .isEqualTo(OptimizeAlertEmailValidationException.ERROR_CODE);
    assertThat(mappedResponse.get(ErrorResponseDto.Fields.errorMessage))
        .asString()
        .isEqualTo(OptimizeAlertEmailValidationException.ERROR_MESSAGE + invalidEmails);
    assertThat(mappedResponse.get(ErrorResponseDto.Fields.detailedMessage))
        .asString()
        .isEqualTo(OptimizeAlertEmailValidationException.ERROR_MESSAGE + invalidEmails);
    assertThat(mappedResponse.get(AlertEmailValidationResponseDto.Fields.invalidAlertEmails))
        .asString()
        .isEqualTo(String.join(", ", invalidEmails));
  }
}
