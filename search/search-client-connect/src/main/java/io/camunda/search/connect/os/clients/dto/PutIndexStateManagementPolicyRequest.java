/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.os.clients.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PutIndexStateManagementPolicyRequest(Policy policy) {

  public record Policy(
      String description,
      @JsonProperty("default_state") String defaultState,
      List<State> states,
      @JsonProperty("ism_template") IsmTemplate ismTemplate) {

    public record State(String name, List<Action> actions, List<Transition> transitions) {

      public record Action(Object delete) {}

      public record Transition(
          @JsonProperty("state_name") String stateName, Conditions conditions) {

        public record Conditions(@JsonProperty("min_index_age") String minIndexAge) {}
      }
    }

    public record IsmTemplate(
        @JsonProperty("index_patterns") List<String> indexPatterns, Integer priority) {}
  }
}
