/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.CreateClusterVariableRequest;
import io.camunda.security.validation.ClusterVariableValidator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClusterVariableRequestValidatorTest {

  private static final int MAX_METADATA_SIZE = 1024;

  // Stub the identifier-based validator so tests focus on metadata validation only.
  private final ClusterVariableRequestValidator validator =
      new ClusterVariableRequestValidator(
          new ClusterVariableValidator(null) {
            @Override
            public List<String> validateGlobalClusterVariableRequestWithValue(
                final String name, final Object value) {
              return List.of();
            }
          },
          MAX_METADATA_SIZE);

  @Test
  void shouldRejectMetadataWithNullKey() {
    // given
    final Map<String, Object> metadata = new HashMap<>();
    metadata.put(null, "value");
    final var request =
        CreateClusterVariableRequest.Builder.create()
            .name("foo")
            .value("bar")
            .metadata(metadata)
            .build();

    // when
    final var result = validator.validateGlobalClusterVariableCreateRequest(request);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().getDetail()).contains("must not contain a null key");
  }

  @Test
  void shouldAcceptMetadataWithNonNullKeys() {
    // given
    final var request =
        CreateClusterVariableRequest.Builder.create()
            .name("foo")
            .value("bar")
            .metadata(Map.of("key", "value", "", "value2"))
            .build();

    // when
    final var result = validator.validateGlobalClusterVariableCreateRequest(request);

    // then
    assertThat(result).isEmpty();
  }
}
