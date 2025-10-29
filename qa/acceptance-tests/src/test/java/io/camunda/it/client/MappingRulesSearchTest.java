/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.MappingRule;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class MappingRulesSearchTest {

  private static CamundaClient camundaClient;

  private static final String MAPPING_RULE_ID_1 = Strings.newRandomValidIdentityId();
  private static final String CLAIM_NAME_1 = "department";
  private static final String CLAIM_VALUE_1 = "engineering";

  private static final String MAPPING_RULE_ID_2 = Strings.newRandomValidIdentityId();
  private static final String CLAIM_NAME_2 = "team";
  private static final String CLAIM_VALUE_2 = "backend";

  @BeforeAll
  static void setup() {
    createMappingRule(MAPPING_RULE_ID_1, CLAIM_NAME_1, CLAIM_VALUE_1);
    assertMappingRuleCreated(MAPPING_RULE_ID_1);

    createMappingRule(MAPPING_RULE_ID_2, CLAIM_NAME_2, CLAIM_VALUE_2);
    assertMappingRuleCreated(MAPPING_RULE_ID_2);
  }

  @Test
  void searchShouldReturnAllMappingRules() {
    // when
    final var mappingRuleSearchResponse =
        camundaClient.newMappingRulesSearchRequest().send().join();

    // then
    assertThat(mappingRuleSearchResponse.items())
        .hasSizeGreaterThanOrEqualTo(2)
        .extracting(MappingRule::getMappingRuleId)
        .contains(MAPPING_RULE_ID_1, MAPPING_RULE_ID_2);
  }

  @Test
  void searchShouldReturnMappingRuleFilteredByClaimName() {
    // when
    final var mappingRuleSearchResponse =
        camundaClient
            .newMappingRulesSearchRequest()
            .filter(fn -> fn.claimName(CLAIM_NAME_1))
            .send()
            .join();

    // then
    assertThat(mappingRuleSearchResponse.items())
        .hasSize(1)
        .first()
        .extracting(MappingRule::getMappingRuleId)
        .isEqualTo(MAPPING_RULE_ID_1);
  }

  @Test
  void searchShouldReturnMappingRuleFilteredByClaimValue() {
    // when
    final var mappingRuleSearchResponse =
        camundaClient
            .newMappingRulesSearchRequest()
            .filter(fn -> fn.claimValue(CLAIM_VALUE_2))
            .send()
            .join();

    // then
    assertThat(mappingRuleSearchResponse.items())
        .hasSize(1)
        .first()
        .extracting(MappingRule::getMappingRuleId)
        .isEqualTo(MAPPING_RULE_ID_2);
  }

  @Test
  void searchShouldReturnMappingRulesWithPaging() {
    // when
    final var mappingRuleSearchResponse =
        camundaClient.newMappingRulesSearchRequest().page(p -> p.limit(1)).send().join();

    // then
    assertThat(mappingRuleSearchResponse.items()).hasSize(1);
  }

  @Test
  void searchShouldReturnMappingRulesWithSorting() {
    // when
    final var mappingRuleSearchResponse =
        camundaClient.newMappingRulesSearchRequest().sort(s -> s.claimName().asc()).send().join();

    // then
    final var mappingRules = mappingRuleSearchResponse.items();

    assertThat(mappingRules).hasSizeGreaterThanOrEqualTo(2);
    assertThat(mappingRules).extracting(r -> r.getClaimName()).isSorted();
  }

  private static void createMappingRule(
      final String mappingRuleId, final String claimName, final String claimValue) {
    camundaClient
        .newCreateMappingRuleCommand()
        .mappingRuleId(mappingRuleId)
        .name("test-" + mappingRuleId)
        .claimName(claimName)
        .claimValue(claimValue)
        .send()
        .join();
  }

  private static void assertMappingRuleCreated(final String mappingRuleId) {
    Awaitility.await()
        .untilAsserted(
            () -> {
              final var mappingRuleSearchResponse =
                  camundaClient
                      .newMappingRulesSearchRequest()
                      .filter(f -> f.mappingRuleId(mappingRuleId))
                      .send()
                      .join();
              assertThat(mappingRuleSearchResponse.items()).hasSize(1);
            });
  }
}
