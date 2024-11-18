/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.application.Profile;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.testcontainers.DefaultTestContainers;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@AutoCloseResources
@Testcontainers
@ZeebeIntegration
final class TempMappingTest {
  public static final String CLIENT_ID = "zeebe-IT";
  public static final String CLIENT_SECRET = "client-secret";
  @TempDir private static Path credentialsCacheDir;
  private static final String KEYCLOAK_USER = "admin";
  private static final String KEYCLOAK_PASSWORD = "admin";
  private static final String KEYCLOAK_PATH_CAMUNDA_REALM = "/realms/Camunda-platform-IT";
  private static final Network NETWORK = Network.newNetwork();

  @Container
  private static final KeycloakContainer KEYCLOAK =
      DefaultTestContainers.createDefaultKeycloak()
          .withEnv("KEYCLOAK_ADMIN_USER", KEYCLOAK_USER)
          .withEnv("KEYCLOAK_ADMIN_PASSWORD", KEYCLOAK_PASSWORD)
          .withEnv("KEYCLOAK_DATABASE_VENDOR", "dev-mem")
          .withNetwork(NETWORK)
          .withNetworkAliases("keycloak")
          .withExposedPorts(8080)
          .withRealmImportFile("keycloak/realm-export.json");

  //  private static AuthorizationsUtil authUtil;
  @AutoCloseResource private static ZeebeClient defaultUserClient;
  private static final int PORT = SocketUtil.getNextAddress().getPort();

  @TestZeebe(autoStart = false)
  private TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withBrokerConfig(
              b -> b.getExperimental().getEngine().getAuthorizations().setEnableAuthorization(true))
          .withBrokerConfig(b -> b.getGateway().getNetwork().setPort(PORT))
          .withAdditionalProfile(Profile.AUTH_OIDC)
          .withProperty(
              "spring.security.oauth2.client.provider.oidcclient.issuer-uri",
              "http://%s:%d%s"
                  .formatted(
                      KEYCLOAK.getHost(),
                      KEYCLOAK.getMappedPort(8080),
                      KEYCLOAK_PATH_CAMUNDA_REALM))
          .withProperty("spring.security.oauth2.client.provider.oidcclient.clientid", CLIENT_ID)
          .withProperty(
              "spring.security.oauth2.client.provider.oidcclient.clientsecret", CLIENT_SECRET)
          .withProperty(
              "spring.security.oauth2.client.provider.oidcclient.registration.oidcclient",
              "oidcclient")
          .withProperty(
              "spring.security.oauth2.client.provider.oidcclient.registration.oidcclient.redirecturi",
              "http://localhost:%d/login/oauth2/code/zeebe-IT".formatted(PORT))
          .withProperty(
              "spring.security.oauth2.client.provider.oidcclient.registration.oidcclient.scope",
              "openid,profile");

  @BeforeEach
  void beforeEach() {
    broker.start();


    defaultUserClient =
        broker
            .newClientBuilder()
            .preferRestOverGrpc(true)
            .credentialsProvider(
                new OAuthCredentialsProviderBuilder()
                    .clientId(CLIENT_ID)
                    .clientSecret(CLIENT_SECRET)
                    .audience(CLIENT_ID)
                    .authorizationServerUrl(
                        "http://localhost:%d%s/protocol/openid-connect/token"
                            .formatted(KEYCLOAK.getFirstMappedPort(), KEYCLOAK_PATH_CAMUNDA_REALM))
                    .credentialsCachePath(credentialsCacheDir.resolve(CLIENT_ID).toString())
                    .build())
            .defaultRequestTimeout(Duration.ofSeconds(10))
            .build();
  }

  @Test
  void shouldBeAuthorizedToDeployWithDefaultUser() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    // when then
    final var deploymentEvent =
        defaultUserClient
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
                "process.bpmn")
            .send()
            .join();
    assertThat(deploymentEvent.getProcesses().getFirst().getBpmnProcessId()).isEqualTo(processId);
  }

  //  @Test
  //  void shouldBeAuthorizedToDeployWithPermissions() {
  //    // given
  //    final var processId = Strings.newRandomValidBpmnId();
  //    final var username = UUID.randomUUID().toString();
  //    final var password = "password";
  //    authUtil.createUserWithPermissions(
  //        username,
  //        password,
  //        new Permissions(ResourceTypeEnum.DEPLOYMENT, PermissionTypeEnum.CREATE, List.of("*")));
  //
  //    try (final var client = authUtil.createClient(username, password)) {
  //      // when
  //      final var deploymentEvent =
  //          client
  //              .newDeployResourceCommand()
  //              .addProcessModel(
  //                  Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
  //                  "process.bpmn")
  //              .send()
  //              .join();
  //
  //      // then
  //
  // assertThat(deploymentEvent.getProcesses().getFirst().getBpmnProcessId()).isEqualTo(processId);
  //    }
  //  }
  //
  //  @Test
  //  void shouldBeUnAuthorizedToDeployWithPermissions() {
  //    // given
  //    final var processId = Strings.newRandomValidBpmnId();
  //    final var username = UUID.randomUUID().toString();
  //    final var password = "password";
  //    authUtil.createUser(username, password);
  //
  //    // when
  //    try (final var client = authUtil.createClient(username, password)) {
  //      final var deployFuture =
  //          client
  //              .newDeployResourceCommand()
  //              .addProcessModel(
  //                  Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
  //                  "process.bpmn")
  //              .send();
  //
  //      // then
  //      assertThatThrownBy(deployFuture::join)
  //          .isInstanceOf(ProblemException.class)
  //          .hasMessageContaining("title: UNAUTHORIZED")
  //          .hasMessageContaining("status: 401")
  //          .hasMessageContaining(
  //              "Unauthorized to perform operation 'CREATE' on resource 'DEPLOYMENT'");
  //    }
  //  }
}
