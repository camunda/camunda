/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.backup;

import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.rest.BackupRequestDto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.SuppressionConstants.UNUSED;

public class BackupRequestValidationTest {

  @ParameterizedTest
  @MethodSource("invalidBackupIds")
  public void triggerBackupWithInvalidBackupId(final Integer invalidBackupId, final String expectedErrorMsg) {
    // when
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    Validator validator = factory.getValidator();
    BackupRequestDto backupRequestDto = new BackupRequestDto(invalidBackupId);
    Set<ConstraintViolation<BackupRequestDto>> violations = validator.validate(backupRequestDto);

    // then
    assertThat(violations).singleElement().extracting(ConstraintViolation::getMessage).isEqualTo(expectedErrorMsg);
  }

  @SuppressWarnings(UNUSED)
  private static Stream<Arguments> invalidBackupIds() {
    return Stream.of(
      Arguments.of(null, "must not be null"),
      Arguments.of(-1, "must be greater than or equal to 0")
    );
  }
}