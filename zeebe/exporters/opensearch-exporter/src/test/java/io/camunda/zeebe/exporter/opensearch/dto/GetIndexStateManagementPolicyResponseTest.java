/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch.dto;

import static io.camunda.zeebe.exporter.opensearch.OpensearchClient.ISM_INITIAL_STATE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration;
import io.camunda.zeebe.exporter.opensearch.dto.GetIndexStateManagementPolicyResponse.Policy;
import io.camunda.zeebe.exporter.opensearch.dto.GetIndexStateManagementPolicyResponse.Policy.IsmTemplate;
import io.camunda.zeebe.exporter.opensearch.dto.GetIndexStateManagementPolicyResponse.Policy.State;
import io.camunda.zeebe.exporter.opensearch.dto.GetIndexStateManagementPolicyResponse.Policy.State.Transition;
import io.camunda.zeebe.exporter.opensearch.dto.GetIndexStateManagementPolicyResponse.Policy.State.Transition.Conditions;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

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
    final Policy policy =
        new Policy(
            name,
            description,
            ISM_INITIAL_STATE,
            List.of(
                new State(
                    ISM_INITIAL_STATE,
                    Collections.emptyList(),
                    List.of(new Transition("delete", new Conditions(minimumAge))))),
            List.of(new IsmTemplate(List.of(indexPattern), 1)));
    return new GetIndexStateManagementPolicyResponse(policy, 1, 1);
  }
}
