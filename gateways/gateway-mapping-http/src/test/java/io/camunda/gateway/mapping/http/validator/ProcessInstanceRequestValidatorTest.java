/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionByKey;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

@DisplayName("ProcessInstanceRequestValidator Tests")
class ProcessInstanceRequestValidatorTest {

  @Test
  @DisplayName("Should accept valid processDefinitionKey format")
  void shouldAcceptValidProcessDefinitionKey() {
    final var request = new ProcessInstanceCreationInstructionByKey();
    request.setProcessDefinitionKey("123456789");

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should accept valid tags")
  void shouldAcceptValidTags() {
    final var request = new ProcessInstanceCreationInstructionByKey();
    request.setProcessDefinitionKey("123456789");
    request.setTags(Set.of("valid-tag", "another-tag"));

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Should reject invalid tags")
  void shouldRejectInvalidTags() {
    final var request = new ProcessInstanceCreationInstructionByKey();
    request.setProcessDefinitionKey("123456789");
    request.setTags(Set.of("1 invalid-tag", "another-tag"));

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateCreateProcessInstanceRequest(request);

    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getTitle()).isEqualTo("Bad Request");
    assertThat(problem.getDetail()).contains("is not valid. Tags must start with a letter");
  }

  @Test
  @DisplayName("Should reject businessId exceeding max length in simple API")
  void shouldRejectBusinessIdExceedingMaxLengthSimpleApi() {
    final var request =
        new io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationInstruction();
    request.setProcessDefinitionId("process-id");
    request.setBusinessId("a".repeat(257));

    final Optional<ProblemDetail> result =
        ProcessInstanceRequestValidator.validateSimpleCreateProcessInstanceRequest(request);

    assertThat(result).isPresent();
    final ProblemDetail problem = result.get();
    assertThat(problem.getTitle()).isEqualTo("Bad Request");
    assertThat(problem.getDetail()).contains("businessId").contains("256");
  }
}
