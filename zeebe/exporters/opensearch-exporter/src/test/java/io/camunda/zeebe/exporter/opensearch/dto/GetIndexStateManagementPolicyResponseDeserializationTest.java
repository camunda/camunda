/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.json.stream.JsonParser;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.ism.GetPolicyResponse;

/**
 * Tests that demonstrate the Integer overflow bug in the OpenSearch Java client's ISM Policy
 * deserialization and verify that the custom deserializer avoids it.
 *
 * <p>The upstream bug: {@code Policy#lastUpdatedTime()} is typed as {@link Integer}, but OpenSearch
 * returns Unix timestamps in milliseconds (e.g. 1739875860042), which exceed {@link
 * Integer#MAX_VALUE} (2147483647) and cause a number format exception during deserialization.
 *
 * @see <a href="https://github.com/opensearch-project/opensearch-java/issues/1246">
 *     opensearch-java#1246</a>
 */
final class GetIndexStateManagementPolicyResponseDeserializationTest {

  // A realistic OpenSearch ISM policy GET response with last_updated_time that overflows Integer.
  // 1739875860042 > Integer.MAX_VALUE (2147483647)
  private static final String ISM_POLICY_RESPONSE_JSON =
      """
      {
        "_id": "zeebe-record",
        "_version": 1,
        "_seq_no": 7,
        "_primary_term": 1,
        "policy": {
          "policy_id": "zeebe-record",
          "description": "Zeebe record retention policy",
          "last_updated_time": 1739875860042,
          "schema_version": 21,
          "error_notification": null,
          "default_state": "initial",
          "states": [
            {
              "name": "initial",
              "actions": [],
              "transitions": [
                {
                  "state_name": "delete",
                  "conditions": {
                    "min_index_age": "30d"
                  }
                }
              ]
            },
            {
              "name": "delete",
              "actions": [
                {
                  "retry": {
                    "count": 3,
                    "backoff": "exponential",
                    "delay": "1m"
                  },
                  "delete": {}
                }
              ],
              "transitions": []
            }
          ],
          "ism_template": [
            {
              "index_patterns": ["zeebe-record*"],
              "priority": 1,
              "last_updated_time": 1739875860042
            }
          ]
        }
      }
      """;

  /**
   * Proves the upstream bug — the library's GetPolicyResponse/Policy deserializer throws when
   * last_updated_time exceeds Integer.MAX_VALUE.
   */
  @Test
  void libraryDeserializerOverflowsOnLastUpdatedTime() {
    final JacksonJsonpMapper mapper = new JacksonJsonpMapper();

    assertThatThrownBy(
            () -> {
              final JsonParser parser =
                  mapper.jsonProvider().createParser(new StringReader(ISM_POLICY_RESPONSE_JSON));
              GetPolicyResponse._DESERIALIZER.deserialize(parser, mapper);
            })
        .as(
            "Library's GetPolicyResponse should fail because last_updated_time (1739875860042) "
                + "overflows Integer.MAX_VALUE (2147483647)")
        .isInstanceOf(Exception.class);
  }

  /**
   * The custom JSONP ObjectBuilderDeserializer based deserializer in
   * GetIndexStateManagementPolicyResponse.DESERIALIZER avoids the overflow by skipping
   * last_updated_time and correctly deserializes all relevant fields in the response.
   */
  @Test
  void customJsonpDeserializerDeserializesAllFields() {
    final JacksonJsonpMapper mapper = new JacksonJsonpMapper();
    final JsonParser parser =
        mapper.jsonProvider().createParser(new StringReader(ISM_POLICY_RESPONSE_JSON));

    final GetIndexStateManagementPolicyResponse response =
        GetIndexStateManagementPolicyResponse.DESERIALIZER.deserialize(parser, mapper);

    // top-level response fields
    assertThat(response).isNotNull();
    assertThat(response.seqNo()).isEqualTo(7);
    assertThat(response.primaryTerm()).isEqualTo(1);

    // policy-level fields
    final var policy = response.policy();
    assertThat(policy).isNotNull();
    assertThat(policy.policyId()).isEqualTo("zeebe-record");
    assertThat(policy.description()).isEqualTo("Zeebe record retention policy");
    assertThat(policy.defaultState()).isEqualTo("initial");
    assertThat(policy.schemaVersion()).isEqualTo(21);
    assertThat(policy.errorNotification()).isNull();
    // last_updated_time is intentionally skipped to avoid Integer overflow
    assertThat(policy.lastUpdatedTime()).isNull();

    // states
    assertThat(policy.states()).hasSize(2);

    // initial state
    final var initialState = policy.states().get(0);
    assertThat(initialState.name()).isEqualTo("initial");
    assertThat(initialState.actions()).isEmpty();
    assertThat(initialState.transitions()).hasSize(1);

    // initial state -> delete transition
    final var transition = initialState.transitions().get(0);
    assertThat(transition.stateName()).isEqualTo("delete");
    assertThat(transition.conditions()).containsKey("min_index_age");
    assertThat(transition.conditions().get("min_index_age").to(String.class)).isEqualTo("30d");

    // delete state
    final var deleteState = policy.states().get(1);
    assertThat(deleteState.name()).isEqualTo("delete");
    assertThat(deleteState.transitions()).isEmpty();
    assertThat(deleteState.actions()).hasSize(1);

    // delete action with retry
    final var deleteAction = deleteState.actions().get(0);
    assertThat(deleteAction.delete()).isNotNull();
    assertThat(deleteAction.retry()).isNotNull();
    assertThat(deleteAction.retry().count()).isEqualTo(3);
    assertThat(deleteAction.retry().backoff()).isEqualTo("exponential");
    assertThat(deleteAction.retry().delay()).isEqualTo("1m");

    // ism_template
    assertThat(policy.ismTemplate()).hasSize(1);
    final var ismTemplate = policy.ismTemplate().get(0);
    assertThat(ismTemplate.indexPatterns()).containsExactly("zeebe-record*");
    assertThat(ismTemplate.priority()).isEqualTo(1);
    // IsmTemplate.lastUpdatedTime is also skipped to avoid overflow
    assertThat(ismTemplate.lastUpdatedTime()).isNull();
  }
}
