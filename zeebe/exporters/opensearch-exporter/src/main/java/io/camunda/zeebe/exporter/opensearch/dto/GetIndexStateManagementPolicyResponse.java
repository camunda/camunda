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
import io.camunda.zeebe.exporter.opensearch.RecordIndexRouter;
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

public record GetIndexStateManagementPolicyResponse(
    Policy policy, Integer seqNo, Integer primaryTerm) {

  public static final JsonpDeserializer<GetIndexStateManagementPolicyResponse> DESERIALIZER =
      ObjectBuilderDeserializer.lazy(
          GetIndexStateManagementPolicyResponse.Builder::new,
          GetIndexStateManagementPolicyResponse::setupDeserializer);

  private static final JsonpDeserializer<IsmTemplate> ISM_TEMPLATE_DESERIALIZER =
      ObjectBuilderDeserializer.lazy(
          IsmTemplate::builder,
          GetIndexStateManagementPolicyResponse::setupIsmTemplateDeserializer);

  private static final JsonpDeserializer<Policy> POLICY_DESERIALIZER =
      ObjectBuilderDeserializer.lazy(
          Policy::builder, GetIndexStateManagementPolicyResponse::setupPolicyDeserializer);

  private static void setupDeserializer(
      final ObjectDeserializer<GetIndexStateManagementPolicyResponse.Builder> deserializer) {
    deserializer.add(
        GetIndexStateManagementPolicyResponse.Builder::policy,
        GetIndexStateManagementPolicyResponse.POLICY_DESERIALIZER,
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
        Policy.Builder::schemaVersion, JsonpDeserializer.numberDeserializer(), "schema_version");
    deserializer.add(
        Policy.Builder::ismTemplate,
        JsonpDeserializer.arrayDeserializer(
            GetIndexStateManagementPolicyResponse.ISM_TEMPLATE_DESERIALIZER),
        "ism_template");

    // Note: ignoring setting `Policy.Builder::lastUpdatedTime` due to `Policy.lastUpdatedTime` is
    // an integer and the actual value in `last_updated_time` is a long that throws overflow
    // exception when deserializing.
    // Also ignoring `Policy.Builder::errorNotification` since it's not used in the exporter
    // and the `ErrorNotification::Builder::destination` also has last updated time field
    // that has the same issue.
  }

  private static void setupIsmTemplateDeserializer(
      final ObjectDeserializer<IsmTemplate.Builder> deserializer) {
    deserializer.add(
        IsmTemplate.Builder::indexPatterns,
        JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
        "index_patterns");

    deserializer.add(
        IsmTemplate.Builder::priority, JsonpDeserializer.numberDeserializer(), "priority");

    // Note: ignoring setting `IsmTemplate.Builder::lastUpdatedTime` due to
    // `IsmTemplate.lastUpdatedTime` is an integer and the actual value in `last_updated_time`
    // field is a long that throws overflow exception when deserializing.
  }

  public boolean equalsConfiguration(final OpensearchExporterConfiguration configuration) {
    final boolean hasEqualName = policy.policyId().equals(configuration.retention.getPolicyName());
    final boolean hasEqualDescription =
        policy.description().equals(configuration.retention.getPolicyDescription());

    return hasEqualName
        && hasEqualDescription
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
    final Optional<IsmTemplate> maybeIsmTemplate = policy.ismTemplate().stream().findFirst();

    if (maybeIsmTemplate.isEmpty()) {
      return false;
    }

    final IsmTemplate ismTemplate = maybeIsmTemplate.get();
    return ismTemplate.indexPatterns().stream()
        .findFirst()
        .map(
            indexPattern ->
                indexPattern.equals(
                    configuration.index.prefix + RecordIndexRouter.INDEX_DELIMITER + "*"))
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
