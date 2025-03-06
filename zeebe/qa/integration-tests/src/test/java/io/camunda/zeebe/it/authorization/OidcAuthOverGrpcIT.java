/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.client.protocol.rest.OwnerTypeEnum;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.client.protocol.rest.ResourceTypeEnum;
import io.camunda.security.configuration.ConfiguredMapping;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import io.camunda.zeebe.test.util.Strings;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
@Disabled("https://github.com/camunda/camunda/issues/26799")
public class OidcAuthOverGrpcIT {

  private static final String DEFAULT_USER_ID = UUID.randomUUID().toString();
  private static final String RESTRICTED_USER_ID = UUID.randomUUID().toString();
  private static final String KEYCLOAK_REALM = "camunda";
  private static final String DEFAULT_CLIENT_ID = "zeebe";
  private static final String DEFAULT_CLIENT_SECRET = "secret";
  private static final String RESTRICTED_CLIENT_ID = "restricted";
  private static final String RESTRICTED_CLIENT_SECRET = "secret";
  private static final String USER_ID_CLAIM_NAME = "sub";

  @Container
  private static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  @AutoClose private static CamundaClient defaultMappingClient;
  @AutoClose private static CamundaClient restrictedClient;

  @TestZeebe(awaitCompleteTopology = false)
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withAuthenticationMethod(AuthenticationMethod.OIDC)
          .withSecurityConfig(
              c -> {
                c.getAuthorizations().setEnabled(true);

                final var oidcConfig = c.getAuthentication().getOidc();
                oidcConfig.setIssuerUri(KEYCLOAK.getAuthServerUrl() + "/realms/" + KEYCLOAK_REALM);
                // The following two properties are only needed for the webapp login flow which we
                // don't test here.
                oidcConfig.setClientId("example");
                oidcConfig.setRedirectUri("example.com");

                c.getInitialization()
                    .setMappings(
                        List.of(new ConfiguredMapping(USER_ID_CLAIM_NAME, DEFAULT_USER_ID)));
              });

  @BeforeAll
  static void setupKeycloak() {
    final var defaultClient = new ClientRepresentation();
    defaultClient.setClientId(DEFAULT_CLIENT_ID);
    defaultClient.setEnabled(true);
    defaultClient.setClientAuthenticatorType("client-secret");
    defaultClient.setSecret(DEFAULT_CLIENT_SECRET);
    defaultClient.setServiceAccountsEnabled(true);

    final var defaultUser = new UserRepresentation();
    defaultUser.setId(DEFAULT_USER_ID);
    defaultUser.setUsername("zeebe-service-account");
    defaultUser.setServiceAccountClientId(DEFAULT_CLIENT_ID);
    defaultUser.setEnabled(true);

    final var restrictedClient = new ClientRepresentation();
    restrictedClient.setClientId(RESTRICTED_CLIENT_ID);
    restrictedClient.setEnabled(true);
    restrictedClient.setClientAuthenticatorType("client-secret");
    restrictedClient.setSecret(RESTRICTED_CLIENT_SECRET);
    restrictedClient.setServiceAccountsEnabled(true);

    final var restrictedUser = new UserRepresentation();
    restrictedUser.setId(RESTRICTED_USER_ID);
    restrictedUser.setUsername("restricted-service-account");
    restrictedUser.setServiceAccountClientId(RESTRICTED_CLIENT_ID);
    restrictedUser.setEnabled(true);

    final var realm = new RealmRepresentation();
    realm.setRealm("camunda");
    realm.setEnabled(true);
    realm.setClients(List.of(defaultClient, restrictedClient));
    realm.setUsers(List.of(defaultUser, restrictedUser));

    try (final var keycloak = KEYCLOAK.getKeycloakAdminClient()) {
      keycloak.realms().create(realm);
    }
  }

  @BeforeEach
  void beforeEach(@TempDir final Path tempDir) {
    defaultMappingClient =
        broker
            .newClientBuilder()
            .preferRestOverGrpc(false)
            .defaultRequestTimeout(Duration.ofSeconds(15))
            .credentialsProvider(
                new OAuthCredentialsProviderBuilder()
                    .clientId(DEFAULT_CLIENT_ID)
                    .clientSecret(DEFAULT_CLIENT_SECRET)
                    .audience("zeebe")
                    .authorizationServerUrl(
                        KEYCLOAK.getAuthServerUrl()
                            + "/realms/"
                            + KEYCLOAK_REALM
                            + "/protocol/openid-connect/token")
                    .credentialsCachePath(tempDir.resolve("default").toString())
                    .build())
            .build();

    restrictedClient =
        broker
            .newClientBuilder()
            .preferRestOverGrpc(false)
            .defaultRequestTimeout(Duration.ofSeconds(15))
            .credentialsProvider(
                new OAuthCredentialsProviderBuilder()
                    .clientId(RESTRICTED_CLIENT_ID)
                    .clientSecret(RESTRICTED_CLIENT_SECRET)
                    .audience("zeebe")
                    .authorizationServerUrl(
                        KEYCLOAK.getAuthServerUrl()
                            + "/realms/"
                            + KEYCLOAK_REALM
                            + "/protocol/openid-connect/token")
                    .credentialsCachePath(tempDir.resolve("restricted").toString())
                    .build())
            .build();
  }

  @Test
  void shouldBeAuthorizedWithDefaultMapping() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    // when
    final var deploymentEvent =
        defaultMappingClient
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
                "process.bpmn")
            .send()
            .join();

    // then
    assertThat(deploymentEvent.getProcesses().getFirst().getBpmnProcessId()).isEqualTo(processId);
  }

  @Test
  void shouldBeUnauthorizedWithMappingWithoutPermissions() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    defaultMappingClient
        .newCreateMappingCommand()
        .claimName(claimName)
        .claimValue(claimValue)
        .name(claimValue)
        .send()
        .join();

    // when
    final var deployFuture =
        restrictedClient
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
                "process.bpmn")
            .send();

    // then
    assertThatThrownBy(deployFuture::join)
        .isInstanceOf(ClientStatusException.class)
        .hasMessageContaining("FORBIDDEN")
        .hasMessageContaining(
            "Insufficient permissions to perform operation 'CREATE' on resource 'RESOURCE'");
  }

  @Test
  void shouldBeAuthorizedWithMappingThatIsGrantedPermissions() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var mapping =
        defaultMappingClient
            .newCreateMappingCommand()
            .claimName(USER_ID_CLAIM_NAME)
            .claimValue(RESTRICTED_USER_ID)
            .name(RESTRICTED_USER_ID)
            .send()
            .join();
    defaultMappingClient
        .newCreateAuthorizationCommand()
        .ownerId(String.valueOf(mapping.getMappingId()))
        .ownerType(OwnerTypeEnum.MAPPING)
        .resourceId("*")
        .resourceType(ResourceTypeEnum.RESOURCE)
        .permissionTypes(PermissionTypeEnum.CREATE)
        .send()
        .join();

    // when
    final var deploymentEvent =
        restrictedClient
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
                "process.bpmn")
            .send()
            .join();

    // then
    assertThat(deploymentEvent.getProcesses().getFirst().getBpmnProcessId()).isEqualTo(processId);
  }
}
