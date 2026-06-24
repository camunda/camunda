/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.net.http.HttpClient;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class MappingRuleIT {
  private static CamundaClient client;

  private static final String EXISTING_RULE_ID = Strings.newRandomValidIdentityId();
  private static final String EXISTING_NAME = "MappingRuleName";
  private static final String EXISTING_CLAIM_NAME = "ClaimName";
  private static final String EXISTING_CLAIM_VALUE = "ClaimValue";

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();

  @BeforeAll
  static void setup() {
    createMappingRule(EXISTING_RULE_ID, EXISTING_NAME, EXISTING_CLAIM_NAME, EXISTING_CLAIM_VALUE);
    assertMappingRuleExists(
        EXISTING_RULE_ID, EXISTING_NAME, EXISTING_CLAIM_NAME, EXISTING_CLAIM_VALUE);
  }

  @Test
  void shouldCreateAndGetMappingRuleById() {
    // given
    final var mappingRuleId = Strings.newRandomValidIdentityId();
    final var name = UUID.randomUUID().toString();
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();

    // when
    createMappingRule(mappingRuleId, name, claimName, claimValue);
    // then
    assertMappingRuleExists(mappingRuleId, name, claimName, claimValue);
  }

  @Test
  void shouldUpdateMappingRule() {
    // given
    final var name = UUID.randomUUID().toString();
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();

    // when
    client
        .newUpdateMappingRuleCommand(EXISTING_RULE_ID)
        .name(name)
        .claimName(claimName)
        .claimValue(claimValue)
        .send()
        .join();

    // then
    assertMappingRuleExists(EXISTING_RULE_ID, name, claimName, claimValue);
  }

  private static void createMappingRule(
      final String mappingRuleId,
      final String name,
      final String claimName,
      final String claimValue) {
    client
        .newCreateMappingRuleCommand()
        .mappingRuleId(mappingRuleId)
        .name(name)
        .claimName(claimName)
        .claimValue(claimValue)
        .send()
        .join();
  }

  private static void assertMappingRuleExists(
      final String mappingRuleId,
      final String name,
      final String claimName,
      final String claimValue) {
    Awaitility.await("Mapping rule exists in the secondary storage system")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var mappingRule = client.newMappingRuleGetRequest(mappingRuleId).send().join();
              assertThat(mappingRule).isNotNull();
              assertThat(mappingRule.getMappingRuleId()).isEqualTo(mappingRuleId);
              assertThat(mappingRule.getName()).isEqualTo(name);
              assertThat(mappingRule.getClaimName()).isEqualTo(claimName);
              assertThat(mappingRule.getClaimValue()).isEqualTo(claimValue);
            });
  }
}
