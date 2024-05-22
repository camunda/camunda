/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.SuppressionConstants.UNUSED;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Stream;
import org.camunda.optimize.dto.optimize.rest.BackupRequestDto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class BackupRequestValidationTest {

  @ParameterizedTest
  @MethodSource("invalidBackupIds")
  public void triggerBackupWithInvalidBackupId(
      final Long invalidBackupId, final String expectedErrorMsg) {
    // when
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    Validator validator = factory.getValidator();
    BackupRequestDto backupRequestDto = new BackupRequestDto(invalidBackupId);
    Set<ConstraintViolation<BackupRequestDto>> violations = validator.validate(backupRequestDto);

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
