/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.response.MappingRule;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.net.http.HttpClient;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
public class GetMappingRuleIntegrationTest {

  private static CamundaClient camundaClient;

  @AutoClose private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @Test
  void shouldGetMappingRule() {
    // given
    final var mappingRuleId = Strings.newRandomValidIdentityId();
    final var claimName = "role";
    final var claimValue = "admin";
    final var name = "Admin Mapping Rule";

    // create a mapping rule first
    camundaClient
        .newCreateMappingRuleCommand()
        .mappingRuleId(mappingRuleId)
        .claimName(claimName)
        .claimValue(claimValue)
        .name(name)
        .send()
        .join();

    // wait for the mapping rule to be created and indexed
    Awaitility.await("Mapping rule is created and indexed")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final MappingRule result =
                  camundaClient.newMappingRuleGetRequest(mappingRuleId).send().join();
              assertThat(result).isNotNull();
            });

    // when - get the mapping rule
    final MappingRule result = camundaClient.newMappingRuleGetRequest(mappingRuleId).send().join();

    // then
    assertThat(result.getMappingRuleId()).isEqualTo(mappingRuleId);
    assertThat(result.getClaimName()).isEqualTo(claimName);
    assertThat(result.getClaimValue()).isEqualTo(claimValue);
    assertThat(result.getName()).isEqualTo(name);
  }

  @Test
  void shouldReturnNotFoundOnGettingNonExistentMappingRule() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newMappingRuleGetRequest("non-existent-mapping-rule-id")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }
}
