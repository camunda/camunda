/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static io.camunda.client.api.search.enums.ResourceType.USER_TASK;
import static io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker.DEFAULT_MAPPING_RULE_CLAIM_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Authorization;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.qa.util.auth.ClientDefinition;
import io.camunda.qa.util.auth.GroupDefinition;
import io.camunda.qa.util.auth.MappingRuleDefinition;
import io.camunda.qa.util.auth.Membership;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.RoleDefinition;
import io.camunda.qa.util.auth.TestClient;
import io.camunda.qa.util.auth.TestGroup;
import io.camunda.qa.util.auth.TestMappingRule;
import io.camunda.qa.util.auth.TestRole;
import io.camunda.qa.util.multidb.CamundaClientTestFactory;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * OIDC variant of the me/authorizations endpoint tests. Covers mapping-rule-owned and client-owned
 * authorizations, both of which require keycloak/OIDC to resolve.
 *
 * <p>See {@link MeAuthorizationsIT} for the basic-auth variant (user, group, role).
 */
@MultiDbTest(setupKeycloak = true)
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class MeAuthorizationsOidcIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withAuthenticationMethod(AuthenticationMethod.OIDC)
          .withAuthorizationsEnabled()
          .withSecurityConfig(c -> c.getAuthorizations().setEnabled(true))
          .withSecurityConfig(c -> c.getAuthentication().getOidc().setClientIdClaim("client_id"));

  // Injected by the MultiDbTest extension; physical-tenant-aware
  private static CamundaClientTestFactory clientFactory;

  private static final String CLIENT_RESOURCE_ID = "meOidcClientResource";

  // A mapping rule whose authorizations we want to see — granted directly to the mapping rule
  @MappingRuleDefinition
  private static final TestMappingRule ME_MAPPING_RULE =
      new TestMappingRule(
          Strings.newRandomValidIdentityId(),
          DEFAULT_MAPPING_RULE_CLAIM_NAME,
          Strings.newRandomValidIdentityId(),
          List.of(
              new Permissions(
                  PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("myMappedProcess"))));

  // A second mapping rule that should never appear in the first one's results
  @MappingRuleDefinition
  private static final TestMappingRule STRANGER_MAPPING_RULE =
      new TestMappingRule(
          Strings.newRandomValidIdentityId(),
          DEFAULT_MAPPING_RULE_CLAIM_NAME,
          Strings.newRandomValidIdentityId(),
          List.of(Permissions.withWildcard(RESOURCE, CREATE)));

  // Group the mapping rule belongs to — its authorizations should appear via group membership
  @GroupDefinition
  private static final TestGroup MAPPING_RULE_GROUP =
      new TestGroup(
          "meOidcGroup",
          "meOidcGroup",
          List.of(Permissions.withWildcard(RESOURCE, CREATE)),
          List.of(new Membership(ME_MAPPING_RULE.id(), EntityType.MAPPING_RULE)));

  // Role the mapping rule belongs to — its authorizations should appear via role membership
  @RoleDefinition
  private static final TestRole MAPPING_RULE_ROLE =
      new TestRole(
          "meOidcRole",
          "meOidcRole",
          List.of(Permissions.withWildcard(USER_TASK, READ)),
          List.of(new Membership(ME_MAPPING_RULE.id(), EntityType.MAPPING_RULE)));

  // A client whose authorizations we want to see — granted directly to the client
  @ClientDefinition
  private static final TestClient ME_CLIENT =
      new TestClient(
          "meOidcClient", List.of(new Permissions(RESOURCE, CREATE, List.of(CLIENT_RESOURCE_ID))));

  // Group the client belongs to — its authorizations should appear via group membership
  @GroupDefinition
  private static final TestGroup CLIENT_GROUP =
      new TestGroup(
          "meOidcClientGroup",
          "meOidcClientGroup",
          List.of(Permissions.withWildcard(USER_TASK, READ)),
          List.of(new Membership("meOidcClient", EntityType.CLIENT)));

  @Test
  void shouldReturnAuthorizationsForMappingRuleDirectlyViaGroupAndViaRole() {
    Awaitility.await()
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final CamundaClient client = clientFactory.getCamundaClient(ME_MAPPING_RULE.id());
              final SearchResponse<Authorization> response =
                  client.newOwnAuthorizationSearchRequest().send().join();
              final var items = response.items();

              // direct mapping-rule authorization
              assertThat(items)
                  .anySatisfy(
                      item -> {
                        assertThat(item.getOwnerId()).isEqualTo(ME_MAPPING_RULE.id());
                        assertThat(item.getOwnerType().name()).isEqualTo("MAPPING_RULE");
                        assertThat(item.getResourceType().name()).isEqualTo("PROCESS_DEFINITION");
                        assertThat(item.getResourceId()).isEqualTo("myMappedProcess");
                      });

              // authorization via group membership
              assertThat(items)
                  .anySatisfy(
                      item -> {
                        assertThat(item.getOwnerId()).isEqualTo(MAPPING_RULE_GROUP.id());
                        assertThat(item.getOwnerType().name()).isEqualTo("GROUP");
                        assertThat(item.getResourceType().name()).isEqualTo("RESOURCE");
                        assertThat(item.getResourceId()).isEqualTo("*");
                      });

              // authorization via role membership
              assertThat(items)
                  .anySatisfy(
                      item -> {
                        assertThat(item.getOwnerId()).isEqualTo(MAPPING_RULE_ROLE.id());
                        assertThat(item.getOwnerType().name()).isEqualTo("ROLE");
                        assertThat(item.getResourceType().name()).isEqualTo("USER_TASK");
                        assertThat(item.getResourceId()).isEqualTo("*");
                      });

              // stranger mapping rule's authorizations must not appear
              assertThat(items)
                  .noneSatisfy(
                      item -> assertThat(item.getOwnerId()).isEqualTo(STRANGER_MAPPING_RULE.id()));
            });
  }

  @Test
  void shouldFilterMappingRuleAuthorizationsByResourceType() {
    Awaitility.await()
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final CamundaClient client = clientFactory.getCamundaClient(ME_MAPPING_RULE.id());
              final SearchResponse<Authorization> response =
                  client
                      .newOwnAuthorizationSearchRequest()
                      .filter(f -> f.resourceType(PROCESS_DEFINITION))
                      .send()
                      .join();
              final var items = response.items();

              assertThat(items)
                  .hasSize(1)
                  .first()
                  .satisfies(
                      item -> {
                        assertThat(item.getOwnerId()).isEqualTo(ME_MAPPING_RULE.id());
                        assertThat(item.getResourceType().name()).isEqualTo("PROCESS_DEFINITION");
                        assertThat(item.getResourceId()).isEqualTo("myMappedProcess");
                      });
            });
  }

  @Test
  void shouldReturnAuthorizationsForClientDirectlyAndViaGroup() {
    Awaitility.await()
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final CamundaClient client = clientFactory.getCamundaClient(ME_CLIENT.clientId());
              final SearchResponse<Authorization> response =
                  client.newOwnAuthorizationSearchRequest().send().join();
              final var items = response.items();

              // direct client authorization
              assertThat(items)
                  .anySatisfy(
                      item -> {
                        assertThat(item.getOwnerId()).isEqualTo(ME_CLIENT.clientId());
                        assertThat(item.getOwnerType().name()).isEqualTo("CLIENT");
                        assertThat(item.getResourceType().name()).isEqualTo("RESOURCE");
                        assertThat(item.getResourceId()).isEqualTo(CLIENT_RESOURCE_ID);
                      });
              // authorization via group membership
              assertThat(items)
                  .anySatisfy(
                      item -> {
                        assertThat(item.getOwnerId()).isEqualTo(CLIENT_GROUP.id());
                        assertThat(item.getOwnerType().name()).isEqualTo("GROUP");
                        assertThat(item.getResourceType().name()).isEqualTo("USER_TASK");
                        assertThat(item.getResourceId()).isEqualTo("*");
                      });
            });
  }
}
