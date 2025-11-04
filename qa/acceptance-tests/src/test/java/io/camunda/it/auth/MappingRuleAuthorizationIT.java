/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.DELETE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE;
import static io.camunda.client.api.search.enums.ResourceType.MAPPING_RULE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.UpdateMappingRuleResponse;
import io.camunda.client.api.search.response.MappingRule;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class MappingRuleAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String UNAUTHORIZED = "unauthorizedUser";
  private static final String UPDATER = "updateUser";
  private static final String DEFAULT_PASSWORD = "password";

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(MAPPING_RULE, CREATE, List.of("*")),
              new Permissions(MAPPING_RULE, READ, List.of("*")),
              new Permissions(MAPPING_RULE, UPDATE, List.of("*")),
              new Permissions(MAPPING_RULE, DELETE, List.of("*"))));

  @UserDefinition
  private static final TestUser RESTRICTED_USER =
      new TestUser(
          RESTRICTED, DEFAULT_PASSWORD, List.of(new Permissions(MAPPING_RULE, READ, List.of("*"))));

  @UserDefinition
  private static final TestUser UNAUTHORIZED_USER =
      new TestUser(UNAUTHORIZED, DEFAULT_PASSWORD, List.of());

  @UserDefinition
  private static final TestUser UPDATE_USER =
      new TestUser(
          UPDATER, DEFAULT_PASSWORD, List.of(new Permissions(MAPPING_RULE, UPDATE, List.of("*"))));

  @Test
  void searchShouldReturnAuthorizedMappingRules(
      @Authenticated(RESTRICTED) final CamundaClient userClient,
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final var id = createRandomMappingRule(adminClient);

    // when
    final Future<SearchResponse<MappingRule>> response =
        userClient.newMappingRulesSearchRequest().send();

    // then
    assertThat(response)
        .succeedsWithin(Duration.ofSeconds(5))
        .extracting(SearchResponse::items, InstanceOfAssertFactories.list(MappingRule.class))
        .isNotEmpty()
        .map(MappingRule::getMappingRuleId)
        .contains(id);
  }

  @Test
  void searchShouldReturnEmptyListWhenUnauthorized(
      @Authenticated(UNAUTHORIZED) final CamundaClient userClient) {
    // when
    final var response = userClient.newMappingRulesSearchRequest().send();

    // then
    assertThat((Future<SearchResponse<MappingRule>>) response)
        .succeedsWithin(Duration.ofSeconds(5))
        .extracting(SearchResponse::items, InstanceOfAssertFactories.list(MappingRule.class))
        .isEmpty();
  }

  @Test
  void deleteMappingRuleShouldReturnForbiddenIfUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient,
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final var id = createRandomMappingRule(adminClient);

    // when / then
    assertThat((Future<?>) camundaClient.newDeleteMappingRuleCommand(id).send())
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .withRootCauseInstanceOf(ProblemException.class)
        .withMessageContaining("403: 'Forbidden'");
  }

  @Test
  void getMappingRuleShouldReturnForbiddenIfUnauthorized(
      @Authenticated(UNAUTHORIZED) final CamundaClient camundaClient,
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final var id = createRandomMappingRule(adminClient);

    // when / then
    assertThat((Future<?>) camundaClient.newMappingRuleGetRequest(id).send())
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .withRootCauseInstanceOf(ProblemException.class)
        .withMessageContaining("403: 'Forbidden'");
  }

  @Test
  void getMappingRuleShouldReturnMappingRuleIfAuthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient,
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final var id = createRandomMappingRule(adminClient);

    // when
    final Future<MappingRule> response = camundaClient.newMappingRuleGetRequest(id).send();

    // then
    assertThat(response)
        .succeedsWithin(Duration.ofSeconds(5))
        .satisfies(mappingRule -> assertThat(mappingRule.getMappingRuleId()).isEqualTo(id));
  }

  @Test
  void deleteMappingRuleShouldDeleteMappingRuleIfAuthorized(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // given
    final var mappingRuleId = createRandomMappingRule(camundaClient);

    // when
    final Future<?> deleted = camundaClient.newDeleteMappingRuleCommand(mappingRuleId).send();

    // then - correctness is asserted in MappingRuleIT, here we just check that it was authorized
    assertThat(deleted).succeedsWithin(Duration.ofSeconds(5));
  }

  @Test
  void updateMappingRuleShouldReturnForbiddenIfUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient camundaClient,
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final var id = createRandomMappingRule(adminClient);

    // when / then
    // the actual values don't really matter at the moment because we don't support fine grained
    // permissions on mapping rules, just coarse (e.g. UPDATE on all mapping rules)
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUpdateMappingRuleCommand(id)
                    .name("name")
                    .claimName("claim")
                    .claimValue("value")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("403: 'Forbidden'");
  }

  @Test
  void shouldUpdateMappingRuleIfAuthorized(
      @Authenticated(UPDATER) final CamundaClient camundaClient,
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    // given
    final var id = createRandomMappingRule(adminClient);

    // when
    final Future<UpdateMappingRuleResponse> result =
        camundaClient
            .newUpdateMappingRuleCommand(id)
            .name(UUID.randomUUID().toString())
            .claimName(UUID.randomUUID().toString())
            .claimValue(UUID.randomUUID().toString())
            .send();

    // then - only check if it's successful, correctness tests are found in MappingRuleIT
    assertThat(result).succeedsWithin(5, java.util.concurrent.TimeUnit.SECONDS);
  }

  private String createRandomMappingRule(final CamundaClient client) {
    final var id = Strings.newRandomValidIdentityId();
    client
        .newCreateMappingRuleCommand()
        .mappingRuleId(id)
        .name(UUID.randomUUID().toString())
        .claimName(UUID.randomUUID().toString())
        .claimValue(UUID.randomUUID().toString())
        .send()
        .join();

    // make sure it's available for get/search afterwards
    Awaitility.await("Mapping rule exists in secondary storage")
        .ignoreExceptionsInstanceOf(IOException.class)
        .untilAsserted(
            () ->
                assertThat((Future<MappingRule>) client.newMappingRuleGetRequest(id).send())
                    .succeedsWithin(5, java.util.concurrent.TimeUnit.SECONDS)
                    .returns(id, MappingRule::getMappingRuleId));

    return id;
  }
}
