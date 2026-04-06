/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

@DisplayName("ProcessInstanceRequestValidator Tests")
class ProcessInstanceRequestValidatorTest {

  @Test
  @DisplayName("Should accept null tags")
  void shouldAcceptNullTags() {
    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceTags(null);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should accept valid tags")
  void shouldAcceptValidTags() {
    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceTags(
            Set.of("valid-tag", "another-tag"));

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should reject invalid tags")
  void shouldRejectInvalidTags() {
    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceTags(
            Set.of("1 invalid-tag", "another-tag"));

    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getTitle()).isEqualTo("Bad Request");
    assertThat(problem.getDetail()).contains("is not valid. Tags must start with a letter");
  }
}
