/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.backup;

import static io.camunda.optimize.util.SuppressionConstants.UNUSED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.optimize.rest.BackupRequestDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class BackupRequestValidationTest {

  // @ParameterizedTest
  @MethodSource("invalidBackupIds")
  public void triggerBackupWithInvalidBackupId(
      final Long invalidBackupId, final String expectedErrorMsg) {
    // when
    final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    final Validator validator = factory.getValidator();
    final BackupRequestDto backupRequestDto = new BackupRequestDto(invalidBackupId);
    final Set<ConstraintViolation<BackupRequestDto>> violations =
        validator.validate(backupRequestDto);

    // then
    assertThat(violations)
        .singleElement()
        .extracting(ConstraintViolation::getMessage)
        .isEqualTo(expectedErrorMsg);
  }

  @SuppressWarnings(UNUSED)
  private static Stream<Arguments> invalidBackupIds() {
    return Stream.of(
        Arguments.of(null, "must not be null"),
        Arguments.of(-1L, "must be greater than or equal to 0"));
  }
}
