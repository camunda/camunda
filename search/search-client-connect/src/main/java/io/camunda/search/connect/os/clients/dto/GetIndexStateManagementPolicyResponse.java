/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.os.clients.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GetIndexStateManagementPolicyResponse(
    Policy policy,
    @JsonProperty("_seq_no") Integer seqNo,
    @JsonProperty("_primary_term") Integer primaryTerm) {

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
