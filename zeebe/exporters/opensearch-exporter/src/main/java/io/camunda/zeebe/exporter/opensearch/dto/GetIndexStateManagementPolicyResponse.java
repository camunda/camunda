/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.zeebe.exporter.opensearch.OpensearchClient;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration;
import io.camunda.zeebe.exporter.opensearch.RecordIndexRouter;
import java.util.List;
import java.util.Optional;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.opensearch.ism.States;
import org.opensearch.client.opensearch.ism.Transition;
import org.opensearch.client.util.ObjectBuilder;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GetIndexStateManagementPolicyResponse(
    IsmPolicyResponse policy,
    @JsonProperty("_seq_no") Integer seqNo,
    @JsonProperty("_primary_term") Integer primaryTerm) {

  public static final JsonpDeserializer<GetIndexStateManagementPolicyResponse> DESERIALIZER =
      ObjectBuilderDeserializer.lazy(
          GetIndexStateManagementPolicyResponse.Builder::new,
          GetIndexStateManagementPolicyResponse::setupDeserializer);

  private static void setupDeserializer(
      final ObjectDeserializer<GetIndexStateManagementPolicyResponse.Builder> deserializer) {
    deserializer.add(
        GetIndexStateManagementPolicyResponse.Builder::policy,
        IsmPolicyResponse.DESERIALIZER,
        "policy");
    deserializer.add(
        GetIndexStateManagementPolicyResponse.Builder::seqNo,
        JsonpDeserializer.integerDeserializer(),
        "_seq_no");
    deserializer.add(
        GetIndexStateManagementPolicyResponse.Builder::primaryTerm,
        JsonpDeserializer.integerDeserializer(),
        "_primary_term");
  }

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
    final Optional<States> maybeState =
        policy.states.stream()
            .filter(state -> OpensearchClient.ISM_INITIAL_STATE.equals(state.name()))
            .findFirst();

    if (maybeState.isEmpty()) {
      return false;
    }

    final States initialState = maybeState.get();
    final Optional<Transition> maybeDeleteTransition =
        initialState.transitions().stream()
            .filter(transition -> OpensearchClient.ISM_DELETE_STATE.equals(transition.stateName()))
            .findFirst();

    return maybeDeleteTransition
        .map(
            s -> {
              final JsonData minIndexAge = s.conditions().getOrDefault("min_index_age", null);

              if (minIndexAge == null) {
                return false;
              }

              final String minIndexAgeValue = minIndexAge.to(String.class);
              return minIndexAgeValue != null
                  && minIndexAgeValue.equals(configuration.retention.getMinimumAge());
            })
        .orElse(false);
  }

  private boolean hasEqualIndexPrefix(final OpensearchExporterConfiguration configuration) {
    final Optional<IsmTemplate> maybeIsmTemplate = policy.ismTemplates.stream().findFirst();

    if (maybeIsmTemplate.isEmpty()) {
      return false;
    }

    final IsmTemplate ismTemplate = maybeIsmTemplate.get();
    return ismTemplate.indexPatterns.stream()
        .findFirst()
        .map(indexPattern -> (configuration.index.prefix + RecordIndexRouter.INDEX_DELIMITER+ "*").equals(indexPattern))
        .orElse(false);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record IsmPolicyResponse(
      @JsonProperty("policy_id") String policyId,
      String description,
      @JsonProperty("default_state") String defaultState,
      List<States> states,
      @JsonProperty("ism_template") List<IsmTemplate> ismTemplates) {

    static final JsonpDeserializer<IsmPolicyResponse> DESERIALIZER =
        ObjectBuilderDeserializer.lazy(
            IsmPolicyResponse.Builder::new, IsmPolicyResponse::setupDeserializer);

    private static void setupDeserializer(
        final ObjectDeserializer<IsmPolicyResponse.Builder> deserializer) {
      deserializer.add(
          IsmPolicyResponse.Builder::policyId, JsonpDeserializer.stringDeserializer(), "policy_id");
      deserializer.add(
          IsmPolicyResponse.Builder::description,
          JsonpDeserializer.stringDeserializer(),
          "description");
      deserializer.add(
          IsmPolicyResponse.Builder::defaultState,
          JsonpDeserializer.stringDeserializer(),
          "default_state");
      deserializer.add(
          IsmPolicyResponse.Builder::states,
          JsonpDeserializer.arrayDeserializer(States._DESERIALIZER),
          "states");
      deserializer.add(
          IsmPolicyResponse.Builder::ismTemplates,
          JsonpDeserializer.arrayDeserializer(IsmTemplate.DESERIALIZER),
          "ism_template");
    }

    static class Builder implements ObjectBuilder<IsmPolicyResponse> {
      private String policyId;
      private String description;
      private String defaultState;
      private List<States> states;
      private List<IsmTemplate> ismTemplates;

      public Builder policyId(final String policyId) {
        this.policyId = policyId;
        return this;
      }

      public Builder description(final String description) {
        this.description = description;
        return this;
      }

      public Builder defaultState(final String defaultState) {
        this.defaultState = defaultState;
        return this;
      }

      public Builder states(final List<States> states) {
        this.states = states;
        return this;
      }

      public Builder ismTemplates(final List<IsmTemplate> ismTemplates) {
        this.ismTemplates = ismTemplates;
        return this;
      }

      @Override
      public IsmPolicyResponse build() {
        return new IsmPolicyResponse(policyId, description, defaultState, states, ismTemplates);
      }
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record IsmTemplate(
      @JsonProperty("index_patterns") List<String> indexPatterns, Integer priority) {

    static final JsonpDeserializer<IsmTemplate> DESERIALIZER =
        ObjectBuilderDeserializer.lazy(IsmTemplate.Builder::new, IsmTemplate::setupDeserializer);

    private static void setupDeserializer(
        final ObjectDeserializer<IsmTemplate.Builder> deserializer) {
      deserializer.add(
          IsmTemplate.Builder::indexPatterns,
          JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
          "index_patterns");
      deserializer.add(
          IsmTemplate.Builder::priority, JsonpDeserializer.integerDeserializer(), "priority");
    }

    static class Builder implements ObjectBuilder<IsmTemplate> {
      private List<String> indexPatterns;
      private Integer priority;

      public Builder indexPatterns(final List<String> indexPatterns) {
        this.indexPatterns = indexPatterns;
        return this;
      }

      public Builder priority(final Integer priority) {
        this.priority = priority;
        return this;
      }

      @Override
      public IsmTemplate build() {
        return new IsmTemplate(indexPatterns, priority);
      }
    }
  }

  static class Builder implements ObjectBuilder<GetIndexStateManagementPolicyResponse> {
    private IsmPolicyResponse policy;
    private Integer seqNo;
    private Integer primaryTerm;

    public Builder policy(final IsmPolicyResponse policy) {
      this.policy = policy;
      return this;
    }

    public Builder seqNo(final Integer seqNo) {
      this.seqNo = seqNo;
      return this;
    }

    public Builder primaryTerm(final Integer primaryTerm) {
      this.primaryTerm = primaryTerm;
      return this;
    }

    @Override
    public GetIndexStateManagementPolicyResponse build() {
      return new GetIndexStateManagementPolicyResponse(policy, seqNo, primaryTerm);
    }
  }
}
