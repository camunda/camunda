/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.MappingRule;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.concurrent.Future;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class MappingRulesByTenantSearchIT {

  private static CamundaClient camundaClient;

  private static final String MAPPING_RULE_ID_1 = "a" + Strings.newRandomValidUsername();
  private static final String MAPPING_RULE_ID_2 = "b" + Strings.newRandomValidUsername();
  private static final String MAPPING_RULE_ID_3 = "c" + Strings.newRandomValidUsername();
  private static final String TENANT_ID = Strings.newRandomValidTenantId();

  @BeforeAll
  static void setup() {
    createMappingRule(MAPPING_RULE_ID_1);
    createMappingRule(MAPPING_RULE_ID_2);
    createMappingRule(MAPPING_RULE_ID_3);

    createTenant(TENANT_ID);

    assignMappingRuleToTenant(MAPPING_RULE_ID_1, TENANT_ID);
    assignMappingRuleToTenant(MAPPING_RULE_ID_2, TENANT_ID);

    waitForFixturesToBeExported();
  }

  @Test
  void shouldReturnMappingRulesByTenant() {
    // when
    final var mappingRules =
        camundaClient.newMappingRulesByTenantSearchRequest(TENANT_ID).send().join();

    // then
    assertThat(mappingRules.items())
        .hasSize(2)
        .extracting(
            MappingRule::getMappingRuleId,
            MappingRule::getClaimName,
            MappingRule::getClaimValue,
            MappingRule::getName)
        .contains(
            tuple(
                MAPPING_RULE_ID_1,
                MAPPING_RULE_ID_1 + "claimName",
                MAPPING_RULE_ID_1 + "claimValue",
                "name"),
            tuple(
                MAPPING_RULE_ID_2,
                MAPPING_RULE_ID_2 + "claimName",
                MAPPING_RULE_ID_2 + "claimValue",
                "name"));
  }

  @Test
  void shouldReturnMappingRulesByTenantFiltered() {
    // when
    final var mappingRules =
        camundaClient
            .newMappingRulesByTenantSearchRequest(TENANT_ID)
            .filter(fn -> fn.mappingRuleId(MAPPING_RULE_ID_1))
            .send()
            .join();

    // then
    assertThat(mappingRules.items())
        .hasSize(1)
        .extracting(MappingRule::getMappingRuleId)
        .containsExactly(MAPPING_RULE_ID_1);
  }

  @Test
  void shouldReturnMappingRuleByTenantSorted() {
    // when
    final var mappingRules =
        camundaClient
            .newMappingRulesByTenantSearchRequest(TENANT_ID)
            .sort(fn -> fn.mappingRuleId().desc())
            .send()
            .join();

    // then
    assertThat(mappingRules.items())
        .hasSize(2)
        .extracting(MappingRule::getMappingRuleId)
        .containsExactly(MAPPING_RULE_ID_2, MAPPING_RULE_ID_1);
  }

  private static void createMappingRule(final String mappingRuleId) {
    camundaClient
        .newCreateMappingRuleCommand()
        .mappingRuleId(mappingRuleId)
        .name("name")
        .claimName(mappingRuleId + "claimName")
        .claimValue(mappingRuleId + "claimValue")
        .send()
        .join();
  }

  private static void createTenant(final String tenantId) {
    camundaClient.newCreateTenantCommand().tenantId(tenantId).name("name").send().join();
  }

  private static void assignMappingRuleToTenant(final String mappingRuleId, final String tenantId) {
    camundaClient
        .newAssignMappingRuleToTenantCommand()
        .mappingRuleId(mappingRuleId)
        .tenantId(tenantId)
        .send()
        .join();
  }

  private static void waitForFixturesToBeExported() {
    Awaitility.await("tenant and mapping rule should be visible in secondary storage")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final Future<SearchResponse<MappingRule>> response =
                  camundaClient.newMappingRulesByTenantSearchRequest(TENANT_ID).send();

              assertThat(response)
                  .succeedsWithin(Duration.ofSeconds(10))
                  .extracting(
                      SearchResponse::items, InstanceOfAssertFactories.list(MappingRule.class))
                  .hasSize(2);
            });
  }
}
