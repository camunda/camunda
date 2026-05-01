/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.protocol.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StagedBuilderRoundtripIT {

  // FAIL_ON_MISSING_CREATOR_PROPERTIES is opt-in; Jackson's default lenient behaviour means
  // @JsonProperty(required=true) is documentation-only unless this feature is enabled.
  // We enable it here to verify that the generated @JsonCreator ctor actually enforces required
  // fields when the caller configures the ObjectMapper to be strict.
  private final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true);

  @Test
  void allRequired_roundtripsViaStagedBuilderAndJackson() throws Exception {
    // given
    final var item =
        UsageMetricsResponseItem.Builder.builder()
            .processInstances(10L)
            .decisionInstances(20L)
            .assignees(30L)
            .build();

    // when
    final String json = mapper.writeValueAsString(item);

    // then
    assertThat(json).contains("\"processInstances\":10");
    final var deserialized = mapper.readValue(json, UsageMetricsResponseItem.class);
    assertThat(deserialized).isEqualTo(item);
  }

  @Test
  void inheritance_roundtripsThroughMergedChain() throws Exception {
    // given
    final var inner =
        UsageMetricsResponseItem.Builder.builder()
            .processInstances(1L)
            .decisionInstances(2L)
            .assignees(3L)
            .build();

    // UsageMetricsResponse chain order (per spec required: [tenants, activeTenants]):
    //   processInstances → decisionInstances → assignees → tenants → activeTenants
    final var response =
        UsageMetricsResponse.Builder.builder()
            .processInstances(100L)
            .decisionInstances(200L)
            .assignees(300L)
            .tenants(Map.of("t1", inner))
            .activeTenants(7L)
            .build();

    // when
    final String json = mapper.writeValueAsString(response);

    // then
    final var deserialized = mapper.readValue(json, UsageMetricsResponse.class);
    assertThat(deserialized).isEqualTo(response);
  }

  @Test
  void jacksonRejectsMissingRequired() {
    // given — password absent; UserRequest requires both username and password
    final String json = "{\"username\":\"alice\"}";

    // then
    assertThatThrownBy(() -> mapper.readValue(json, UserRequest.class))
        .isInstanceOf(MismatchedInputException.class);
  }
}
