/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch.dto;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.opensearch.OpensearchClient;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.ism.IsmTemplate;
import org.opensearch.client.opensearch.ism.Policy;
import org.opensearch.client.opensearch.ism.States;

final class GetIndexStateManagementPolicyResponseTest {

  private final OpensearchExporterConfiguration config = new OpensearchExporterConfiguration();
  private final String indexPattern = config.index.prefix + "_*";

  @Test
  void shouldEqualConfiguration() {
    // given
    final var response =
        createResponse(
            config.retention.getPolicyName(),
            config.retention.getPolicyDescription(),
            config.retention.getMinimumAge(),
            indexPattern);

    // when
    final var equal = response.equalsConfiguration(config);

    // then
    assertThat(equal).as("Response equals configuration").isTrue();
  }

  @Test
  void shouldNotEqualConfigurationWhenDifferentName() {
    // given
    final var response =
        createResponse(
            "name",
            config.retention.getPolicyDescription(),
            config.retention.getMinimumAge(),
            indexPattern);

    // when
    final var equal = response.equalsConfiguration(config);

    // then
    assertThat(equal).as("Response does not equal configuration").isFalse();
  }

  @Test
  void shouldNotEqualConfigurationWhenDifferentDescription() {
    // given
    final var response =
        createResponse(
            config.retention.getPolicyName(),
            "description",
            config.retention.getMinimumAge(),
            indexPattern);

    // when
    final var equal = response.equalsConfiguration(config);

    // then
    assertThat(equal).as("Response does not equal configuration").isFalse();
  }

  @Test
  void shouldNotEqualConfigurationWhenDifferentMinimumAge() {
    // given
    final var response =
        createResponse(
            config.retention.getPolicyName(),
            config.retention.getPolicyDescription(),
            "100d",
            indexPattern);

    // when
    final var equal = response.equalsConfiguration(config);

    // then
    assertThat(equal).as("Response does not equal configuration").isFalse();
  }

  @Test
  void shouldNotEqualConfigurationWhenDifferentIndexPattern() {
    // given
    final var response =
        createResponse(
            config.retention.getPolicyName(),
            config.retention.getPolicyDescription(),
            config.retention.getMinimumAge(),
            "foo*");

    // when
    final var equal = response.equalsConfiguration(config);

    // then
    assertThat(equal).as("Response does not equal configuration").isFalse();
  }

  private static GetIndexStateManagementPolicyResponse createResponse(
      final String name,
      final String description,
      final String minimumAge,
      final String indexPattern) {
    final States deleteState =
        States.builder()
            .name(OpensearchClient.ISM_INITIAL_STATE)
            .transitions(
                t -> t.stateName("delete").conditions("min_index_age", JsonData.of(minimumAge)))
            .build();

    final Policy policyResponse =
        Policy.builder()
            .policyId(name)
            .description(description)
            .defaultState(OpensearchClient.ISM_INITIAL_STATE)
            .states(List.of(deleteState))
            .ismTemplate(
                List.of(
                    IsmTemplate.builder().indexPatterns(List.of(indexPattern)).priority(1).build()))
            .build();

    return new GetIndexStateManagementPolicyResponse(policyResponse, 1, 1);
  }
}
