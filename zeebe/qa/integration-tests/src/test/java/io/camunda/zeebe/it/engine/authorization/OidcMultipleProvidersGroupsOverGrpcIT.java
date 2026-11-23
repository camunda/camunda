/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.security.configuration.ConfiguredMappingRule;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.ProvidersConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Two OIDC providers, one realm each. Only the additional provider reads the groups from a claim.
 * The claim is {@code azp}, the client ID. The client {@code partner-client} exists in both realms,
 * so both tokens have the same {@code azp} and only the issuer tells them apart.
 */
@Testcontainers
@ZeebeIntegration
public class OidcMultipleProvidersGroupsOverGrpcIT {

  private static final String ADMIN_USER_ID = UUID.randomUUID().toString();
  private static final String ADMIN_CLIENT_ID = "zeebe";
  private static final String PARTNER_CLIENT_ID = "partner-client";
  private static final String CLIENT_SECRET = "secret";
  private static final String DEFAULT_REALM = "default-realm";
  private static final String PARTNER_REALM = "partner-realm";

  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  @Container
  private static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  @TestZeebe(awaitCompleteTopology = false)
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withAuthenticatedAccess()
          .withAuthenticationMethod(AuthenticationMethod.OIDC)
          .withCamundaExporter("http://" + CONTAINER.getHttpHostAddress())
          .withProperty(
              "camunda.data.secondary-storage.elasticsearch.url",
              "http://" + CONTAINER.getHttpHostAddress())
          .withSecurityConfig(
              c -> {
                c.getAuthorizations().setEnabled(true);

                // The default provider has no groups claim.
                final var defaultOidc = c.getAuthentication().getOidc();
                defaultOidc.setIssuerUri(issuer(DEFAULT_REALM));
                // The client ID and redirect URI are only needed for the webapp login flow, which
                // is not tested here.
                defaultOidc.setClientId("example");
                defaultOidc.setRedirectUri("example.com");

                final var partnerOidc = new OidcAuthenticationConfiguration();
                partnerOidc.setIssuerUri(issuer(PARTNER_REALM));
                partnerOidc.setClientId("example");
                partnerOidc.setRedirectUri("example.com");
                partnerOidc.setGroupsClaim("azp");
                final var providers = new ProvidersConfiguration();
                providers.setOidc(Map.of("partner", partnerOidc));
                c.getAuthentication().setProviders(providers);

                c.getInitialization()
                    .setMappingRules(
                        List.of(new ConfiguredMappingRule(ADMIN_USER_ID, "sub", ADMIN_USER_ID)));
                c.getInitialization()
                    .getDefaultRoles()
                    .put("admin", Map.of("mappingRules", List.of(ADMIN_USER_ID)));
              });

  @BeforeAll
  static void setupKeycloak() {
    try (final var keycloak = KEYCLOAK.getKeycloakAdminClient()) {
      keycloak
          .realms()
          .create(
              realm(
                  DEFAULT_REALM,
                  client(ADMIN_CLIENT_ID),
                  user(ADMIN_USER_ID, "admin-service-account", ADMIN_CLIENT_ID),
                  client(PARTNER_CLIENT_ID),
                  user(
                      UUID.randomUUID().toString(), "default-partner-account", PARTNER_CLIENT_ID)));
      keycloak
          .realms()
          .create(
              realm(
                  PARTNER_REALM,
                  client(PARTNER_CLIENT_ID),
                  user(
                      UUID.randomUUID().toString(), "partner-partner-account", PARTNER_CLIENT_ID)));
    }
  }

  @Test
  void shouldResolveGroupsFromTheClaimOfTheProviderThatIssuedTheToken(@TempDir final Path tempDir) {
    // given a group, with the permission to deploy, whose ID is the client ID of both partner
    // clients
    try (final var admin = createClient(DEFAULT_REALM, ADMIN_CLIENT_ID, tempDir.resolve("admin"));
        final var partnerOfPartnerRealm =
            createClient(PARTNER_REALM, PARTNER_CLIENT_ID, tempDir.resolve("partner"));
        final var partnerOfDefaultRealm =
            createClient(DEFAULT_REALM, PARTNER_CLIENT_ID, tempDir.resolve("default"))) {
      admin.newCreateGroupCommand().groupId(PARTNER_CLIENT_ID).name("partner").send().join();
      admin
          .newCreateAuthorizationCommand()
          .ownerId(PARTNER_CLIENT_ID)
          .ownerType(OwnerType.GROUP)
          .resourceId("*")
          .resourceType(ResourceType.RESOURCE)
          .permissionTypes(PermissionType.CREATE)
          .send()
          .join();

      // when the client of the provider with a groups claim deploys
      final var processId = Strings.newRandomValidBpmnId();
      final var deployment =
          partnerOfPartnerRealm
              .newDeployResourceCommand()
              .addProcessModel(
                  Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
                  "process.bpmn")
              .send()
              .join();

      // then its group, read from the claim, grants the permission
      assertThat(deployment.getProcesses().getFirst().getBpmnProcessId()).isEqualTo(processId);

      // and the client of the provider without a groups claim does not get that group, although
      // its token has the same azp
      final var forbidden =
          partnerOfDefaultRealm
              .newDeployResourceCommand()
              .addProcessModel(
                  Bpmn.createExecutableProcess(Strings.newRandomValidBpmnId())
                      .startEvent()
                      .endEvent()
                      .done(),
                  "process.bpmn")
              .send();
      assertThatThrownBy(forbidden::join)
          .isInstanceOf(ClientStatusException.class)
          .hasMessageContaining("FORBIDDEN")
          .hasMessageContaining(
              "Insufficient permissions to perform operation 'CREATE' on resource 'RESOURCE'");
    }
  }

  private CamundaClient createClient(final String realm, final String clientId, final Path cache) {
    return CamundaClient.newClientBuilder()
        .grpcAddress(broker.grpcAddress())
        .restAddress(broker.restAddress())
        .preferRestOverGrpc(false)
        .defaultRequestTimeout(Duration.ofSeconds(15))
        .credentialsProvider(
            new OAuthCredentialsProviderBuilder()
                .clientId(clientId)
                .clientSecret(CLIENT_SECRET)
                .audience("zeebe")
                .authorizationServerUrl(issuer(realm) + "/protocol/openid-connect/token")
                .credentialsCachePath(cache.toString())
                .build())
        .build();
  }

  private static String issuer(final String realm) {
    return KEYCLOAK.getAuthServerUrl() + "/realms/" + realm;
  }

  private static RealmRepresentation realm(
      final String name,
      final ClientRepresentation client1,
      final UserRepresentation user1,
      final Object... more) {
    final var realm = new RealmRepresentation();
    realm.setRealm(name);
    realm.setEnabled(true);
    final var clients = new java.util.ArrayList<ClientRepresentation>(List.of(client1));
    final var users = new java.util.ArrayList<UserRepresentation>(List.of(user1));
    for (final var item : more) {
      if (item instanceof final ClientRepresentation c) {
        clients.add(c);
      } else if (item instanceof final UserRepresentation u) {
        users.add(u);
      }
    }
    realm.setClients(clients);
    realm.setUsers(users);
    return realm;
  }

  private static ClientRepresentation client(final String clientId) {
    final var client = new ClientRepresentation();
    client.setClientId(clientId);
    client.setEnabled(true);
    client.setClientAuthenticatorType("client-secret");
    client.setSecret(CLIENT_SECRET);
    client.setServiceAccountsEnabled(true);
    return client;
  }

  private static UserRepresentation user(
      final String id, final String username, final String clientId) {
    final var user = new UserRepresentation();
    user.setId(id);
    user.setUsername(username);
    user.setServiceAccountClientId(clientId);
    user.setEnabled(true);
    return user;
  }
}
