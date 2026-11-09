/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.UserTaskCompletionRequest;
import io.camunda.gateway.protocol.model.VariableMetadata;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UserTaskRequestValidatorTest {

  @Test
  void shouldValidateVariablesMetadata() {
    // given
    final Map<String, VariableMetadata> metadata = new HashMap<>();
    metadata.put("card", null);
    final var request =
        UserTaskCompletionRequest.Builder.create()
            .variables(Map.of("card", "1234"))
            .variablesMetadata(metadata)
            .build();

    // when
    final var result = UserTaskRequestValidator.validateCompletionRequest(request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("'card'");
  }
}
