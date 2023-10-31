/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.opensearch.dto;

import static io.camunda.zeebe.exporter.opensearch.OpensearchClient.ISM_DELETE_STATE;
import static io.camunda.zeebe.exporter.opensearch.OpensearchClient.ISM_INITIAL_STATE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration;
import io.camunda.zeebe.exporter.opensearch.dto.GetIndexStateManagementPolicyResponse.Policy.IsmTemplate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GetIndexStateManagementPolicyResponse(
    Policy policy,
    @JsonProperty("_seq_no") Integer seqNo,
    @JsonProperty("_primary_term") Integer primaryTerm) {

  @JsonIgnore
  public boolean equalsConfiguration(final OpensearchExporterConfiguration configuration) {
    final var nameEqual = policy.policyId.equals(configuration.retention.getPolicyName());
    final var descriptionEqual =
        policy.description.equals(configuration.retention.getPolicyDescription());

    final var initialStateOptional =
        policy.states.stream().filter(state -> state.name.equals(ISM_INITIAL_STATE)).findFirst();
    if (initialStateOptional.isEmpty()) {
      return false;
    }

    final var deleteTransitionOptional =
        initialStateOptional.get().transitions.stream()
            .filter(transition -> transition.stateName.equals(ISM_DELETE_STATE))
            .findFirst();
    if (deleteTransitionOptional.isEmpty()) {
      return false;
    }

    final var deleteTransition = deleteTransitionOptional.get();
    final var minIndexAgeEqual =
        deleteTransition.conditions.minIndexAge.equals(configuration.retention.getMinimumAge());

    final var ismTemplateOptional = policy.ismTemplate.stream().findFirst();
    if (ismTemplateOptional.isEmpty()) {
      return false;
    }

    final var indexPatternOptional = ismTemplateOptional.get().indexPatterns.stream().findFirst();
    if (indexPatternOptional.isEmpty()) {
      return false;
    }

    final var indexPatternEqual =
        indexPatternOptional.get().equals(configuration.index.prefix + "*");

    return nameEqual && descriptionEqual && minIndexAgeEqual && indexPatternEqual;
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
