/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch.dto;

import io.camunda.zeebe.exporter.opensearch.OpensearchClient;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration;
import java.util.Optional;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.opensearch.ism.IsmTemplate;
import org.opensearch.client.opensearch.ism.Policy;
import org.opensearch.client.opensearch.ism.States;
import org.opensearch.client.opensearch.ism.Transition;
import org.opensearch.client.util.ObjectBuilder;

/**
 * Custom response DTO for the OpenSearch ISM Get/Put Policy API.
 *
 * <p>We cannot use the library's {@code GetPolicyResponse} / {@code Policy._DESERIALIZER} because
 * {@code Policy.lastUpdatedTime} and {@code IsmTemplate.lastUpdatedTime} are typed as {@code
 * Integer}, but OpenSearch returns Unix timestamps in milliseconds (e.g. {@code 1739875860042})
 * which overflow {@code Integer.MAX_VALUE}. This response type uses custom deserializers for {@code
 * Policy} and {@code IsmTemplate} that ignore the {@code last_updated_time} field, while reusing
 * all other library types ({@link States}, {@link Transition}, etc.) directly.
 *
 * @see <a href="https://github.com/opensearch-project/opensearch-java/issues/1246">
 *     opensearch-java#1246</a>
 */
public record GetIndexStateManagementPolicyResponse(
    Policy policy, Integer seqNo, Integer primaryTerm) {

  /**
   * Deserializer for {@link Policy} that skips the {@code last_updated_time} field to avoid the
   * upstream Integer overflow bug. Uses the library's {@code Policy.Builder} and delegates to
   * {@code States._DESERIALIZER} / {@code IsmTemplate} deserializer for nested types.
   */
  private static final JsonpDeserializer<Policy> POLICY_DESERIALIZER =
      ObjectBuilderDeserializer.lazy(
          Policy::builder, GetIndexStateManagementPolicyResponse::setupPolicyDeserializer);

  /**
   * Deserializer for {@link IsmTemplate} that skips the {@code last_updated_time} field. Same
   * overflow workaround as {@link #POLICY_DESERIALIZER}.
   */
  private static final JsonpDeserializer<IsmTemplate> ISM_TEMPLATE_DESERIALIZER =
      ObjectBuilderDeserializer.lazy(
          IsmTemplate::builder,
          GetIndexStateManagementPolicyResponse::setupIsmTemplateDeserializer);

  public static final JsonpDeserializer<GetIndexStateManagementPolicyResponse> DESERIALIZER =
      ObjectBuilderDeserializer.lazy(
          Builder::new, GetIndexStateManagementPolicyResponse::setupDeserializer);

  private static void setupPolicyDeserializer(
      final ObjectDeserializer<Policy.Builder> deserializer) {
    deserializer.add(Policy.Builder::policyId, JsonpDeserializer.stringDeserializer(), "policy_id");
    deserializer.add(
        Policy.Builder::description, JsonpDeserializer.stringDeserializer(), "description");
    deserializer.add(
        Policy.Builder::defaultState, JsonpDeserializer.stringDeserializer(), "default_state");
    deserializer.add(
        Policy.Builder::states,
        JsonpDeserializer.arrayDeserializer(States._DESERIALIZER),
        "states");
    deserializer.add(
        Policy.Builder::ismTemplate,
        JsonpDeserializer.arrayDeserializer(ISM_TEMPLATE_DESERIALIZER),
        "ism_template");
    deserializer.add(
        Policy.Builder::schemaVersion, JsonpDeserializer.numberDeserializer(), "schema_version");
    // last_updated_time intentionally omitted — see class Javadoc
    deserializer.ignore("last_updated_time");
  }

  private static void setupIsmTemplateDeserializer(
      final ObjectDeserializer<IsmTemplate.Builder> deserializer) {
    deserializer.add(
        IsmTemplate.Builder::indexPatterns,
        JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
        "index_patterns");
    deserializer.add(
        IsmTemplate.Builder::priority, JsonpDeserializer.integerDeserializer(), "priority");
    // last_updated_time intentionally omitted — see class Javadoc
    deserializer.ignore("last_updated_time");
  }

  private static void setupDeserializer(final ObjectDeserializer<Builder> deserializer) {
    deserializer.add(Builder::policy, POLICY_DESERIALIZER, "policy");
    deserializer.add(Builder::seqNo, JsonpDeserializer.integerDeserializer(), "_seq_no");
    deserializer.add(
        Builder::primaryTerm, JsonpDeserializer.integerDeserializer(), "_primary_term");
  }

  public boolean equalsConfiguration(final OpensearchExporterConfiguration configuration) {
    return policy.policyId().equals(configuration.retention.getPolicyName())
        && policy.description().equals(configuration.retention.getPolicyDescription())
        && hasEqualMinimumAge(configuration)
        && hasEqualIndexPrefix(configuration);
  }

  private boolean hasEqualMinimumAge(final OpensearchExporterConfiguration configuration) {
    final Optional<States> maybeState =
        policy.states().stream()
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
    return policy.ismTemplate().stream()
        .findFirst()
        .flatMap(t -> t.indexPatterns().stream().findFirst())
        .map(indexPattern -> (configuration.index.prefix + "*").equals(indexPattern))
        .orElse(false);
  }

  static class Builder implements ObjectBuilder<GetIndexStateManagementPolicyResponse> {
    private Policy policy;
    private Integer seqNo;
    private Integer primaryTerm;

    public Builder policy(final Policy policy) {
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
