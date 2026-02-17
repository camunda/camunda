/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch.dto;

import static io.camunda.zeebe.exporter.opensearch.OpensearchClient.ISM_DELETE_STATE;
import static io.camunda.zeebe.exporter.opensearch.OpensearchClient.ISM_INITIAL_STATE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration;
import io.camunda.zeebe.exporter.opensearch.RecordIndexRouter;
import io.camunda.zeebe.exporter.opensearch.dto.GetIndexStateManagementPolicyResponse.Policy.IsmTemplate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GetIndexStateManagementPolicyResponse(
    Policy policy,
    @JsonProperty("_seq_no") Integer seqNo,
    @JsonProperty("_primary_term") Integer primaryTerm) {

  @JsonIgnore
  public boolean equalsConfiguration(final OpensearchExporterConfiguration configuration) {
    final boolean hasEqualName = policy.policyId.equals(configuration.retention.getPolicyName());
    final boolean hasEqualDescription =
        policy.description.equals(configuration.retention.getPolicyDescription());

    return hasEqualName
        && hasEqualDescription
        && hasEqualMinimumAge(configuration)
        && hasEqualIndexPrefix(configuration);
  }

  private boolean hasEqualMinimumAge(final OpensearchExporterConfiguration configuration) {
    final var initialState =
        policy.states.stream().filter(state -> state.name.equals(ISM_INITIAL_STATE)).findFirst();

    if (initialState.isEmpty()) {
      return false;
    }

    final var deleteTransition =
        initialState.get().transitions.stream()
            .filter(transition -> transition.stateName.equals(ISM_DELETE_STATE))
            .findFirst();

    return deleteTransition
        .filter(
            transition ->
                transition.conditions.minIndexAge.equals(configuration.retention.getMinimumAge()))
        .isPresent();
  }

  private boolean hasEqualIndexPrefix(final OpensearchExporterConfiguration configuration) {
    final var ismTemplate = policy.ismTemplate.stream().findFirst();

    if (ismTemplate.isEmpty()) {
      return false;
    }

    final var indexPattern = ismTemplate.get().indexPatterns.stream().findFirst();

    return indexPattern
        .map(s -> s.equals(configuration.index.prefix + RecordIndexRouter.INDEX_DELIMITER + "*"))
        .orElse(false);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Policy(
      @JsonProperty("policy_id") String policyId,
      String description,
      @JsonProperty("default_state") String defaultState,
      List<State> states,
      @JsonProperty("ism_template") List<IsmTemplate> ismTemplate) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record State(String name, List<Action> actions, List<Transition> transitions) {

      @JsonIgnoreProperties(ignoreUnknown = true)
      public record Action(Object delete) {}

      @JsonIgnoreProperties(ignoreUnknown = true)
      public record Transition(
          @JsonProperty("state_name") String stateName, Conditions conditions) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Conditions(@JsonProperty("min_index_age") String minIndexAge) {}
      }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IsmTemplate(
        @JsonProperty("index_patterns") List<String> indexPatterns, Integer priority) {}
  }
}
