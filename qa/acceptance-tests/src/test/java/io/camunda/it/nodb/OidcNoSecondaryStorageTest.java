/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.nodb;

import static io.camunda.spring.utils.DatabaseTypeUtils.CAMUNDA_DATABASE_TYPE_NONE;
import static io.camunda.spring.utils.DatabaseTypeUtils.PROPERTY_CAMUNDA_DATABASE_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.security.configuration.ConfiguredMappingRule;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import io.camunda.zeebe.test.util.Strings;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Acceptance test to verify that OIDC authentication works correctly with no secondary storage.
 * This test validates the authentication flow when database.type=none and ensures that OIDC users
 * can authenticate and perform basic operations without requiring secondary storage for user data.
 */
@Testcontainers
@ZeebeIntegration
public class OidcNoSecondaryStorageTest {

  private static final String DEFAULT_USER_ID = UUID.randomUUID().toString();
  private static final String SERVICE_CLIENT_ID = UUID.randomUUID().toString();
  private static final String KEYCLOAK_REALM = "camunda";
  private static final String DEFAULT_CLIENT_ID = "zeebe";
  private static final String DEFAULT_CLIENT_SECRET = "secret";
  private static final String SERVICE_CLIENT_NAME = "service";
  private static final String SERVICE_CLIENT_SECRET = "service-secret";
  private static final String USER_ID_CLAIM_NAME = "sub";

  @Container
  private static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  @AutoClose private static CamundaClient defaultClient;
  @AutoClose private static CamundaClient serviceClient;

  @TestZeebe(awaitCompleteTopology = false)
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withAuthenticatedAccess()
          .withAuthenticationMethod(AuthenticationMethod.OIDC)
          .withProperty(PROPERTY_CAMUNDA_DATABASE_TYPE, CAMUNDA_DATABASE_TYPE_NONE)
          .withProperty("camunda.security.authentication.method", "oidc")
          .withSecurityConfig(
              c -> {
                c.getAuthorizations().setEnabled(true);

                final var oidcConfig = c.getAuthentication().getOidc();
                oidcConfig.setIssuerUri(KEYCLOAK.getAuthServerUrl() + "/realms/" + KEYCLOAK_REALM);
                // Required for OIDC configuration even if not used in this test
                oidcConfig.setClientId("example");
                oidcConfig.setRedirectUri("example.com");

                c.getInitialization()
                    .setMappingRules(
                        List.of(
                            new ConfiguredMappingRule(
                                DEFAULT_USER_ID, USER_ID_CLAIM_NAME, DEFAULT_USER_ID),
                            new ConfiguredMappingRule(
                                SERVICE_CLIENT_ID, USER_ID_CLAIM_NAME, SERVICE_CLIENT_ID)));
                c.getInitialization()
                    .getDefaultRoles()
                    .put("admin", Map.of("users", List.of(DEFAULT_USER_ID, SERVICE_CLIENT_ID)));
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

    final var serviceClientRep = new ClientRepresentation();
    serviceClientRep.setClientId(SERVICE_CLIENT_NAME);
    serviceClientRep.setEnabled(true);
    serviceClientRep.setClientAuthenticatorType("client-secret");
    serviceClientRep.setSecret(SERVICE_CLIENT_SECRET);
    serviceClientRep.setServiceAccountsEnabled(true);

    final var serviceUser = new UserRepresentation();
    serviceUser.setId(SERVICE_CLIENT_ID);
    serviceUser.setUsername("service-account");
    serviceUser.setServiceAccountClientId(SERVICE_CLIENT_NAME);
    serviceUser.setEnabled(true);

    final var realm = new RealmRepresentation();
    realm.setRealm("camunda");
    realm.setEnabled(true);
    realm.setClients(List.of(defaultClient, serviceClientRep));
    realm.setUsers(List.of(defaultUser, serviceUser));

    try (final var keycloak = KEYCLOAK.getKeycloakAdminClient()) {
      keycloak.realms().create(realm);
    }
  }

  @BeforeEach
  void beforeEach(@TempDir final Path tempDir) {
    defaultClient =
        CamundaClient.newClientBuilder()
            .grpcAddress(broker.grpcAddress())
            .usePlaintext()
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

    serviceClient =
        CamundaClient.newClientBuilder()
            .grpcAddress(broker.grpcAddress())
            .usePlaintext()
            .defaultRequestTimeout(Duration.ofSeconds(15))
            .credentialsProvider(
                new OAuthCredentialsProviderBuilder()
                    .clientId(SERVICE_CLIENT_NAME)
                    .clientSecret(SERVICE_CLIENT_SECRET)
                    .audience("zeebe")
                    .authorizationServerUrl(
                        KEYCLOAK.getAuthServerUrl()
                            + "/realms/"
                            + KEYCLOAK_REALM
                            + "/protocol/openid-connect/token")
                    .credentialsCachePath(tempDir.resolve("service").toString())
                    .build())
            .build();
  }

  @Test
  void shouldHandleServiceTasksWithOidcAndNoSecondaryStorage() {
    // given - OIDC configured with no secondary storage and a process with service task
    final var processId = Strings.newRandomValidBpmnId();
    final var jobType = "test-service";
    final var process = createProcessWithServiceTask(processId, jobType);

    // when - deploying and executing process with job handling
    serviceClient
        .newDeployResourceCommand()
        .addProcessModel(process, processId + ".bpmn")
        .send()
        .join();

    final var processInstance =
        serviceClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .send()
            .join();

    final var activatedJobs =
        serviceClient.newActivateJobsCommand().jobType(jobType).maxJobsToActivate(1).send().join();

    // then - job processing should work without secondary storage
    assertThat(processInstance.getProcessInstanceKey()).isPositive();
    assertThat(activatedJobs.getJobs()).hasSize(1);

    final var job = activatedJobs.getJobs().getFirst();
    assertThat(job.getType()).isEqualTo(jobType);

    // Complete the job
    serviceClient.newCompleteCommand(job.getKey()).send().join();
  }

  @Test
  void shouldSupportMultipleProcessesWithOidcAndNoSecondaryStorage() {
    // given - multiple processes in OIDC + no secondary storage environment
    final var processId1 = Strings.newRandomValidBpmnId();
    final var processId2 = Strings.newRandomValidBpmnId();
    final var process1 = createSimpleProcess(processId1);
    final var process2 = createSimpleProcess(processId2);

    // when - deploying multiple processes
    defaultClient
        .newDeployResourceCommand()
        .addProcessModel(process1, processId1 + ".bpmn")
        .addProcessModel(process2, processId2 + ".bpmn")
        .send()
        .join();

    final var instance1 =
        defaultClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processId1)
            .latestVersion()
            .withResult()
            .send()
            .join();

    final var instance2 =
        defaultClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processId2)
            .latestVersion()
            .withResult()
            .send()
            .join();

    // then - both processes should execute successfully
    assertThat(instance1.getBpmnProcessId()).isEqualTo(processId1);
    assertThat(instance1.getProcessInstanceKey()).isPositive();

    assertThat(instance2.getBpmnProcessId()).isEqualTo(processId2);
    assertThat(instance2.getProcessInstanceKey()).isPositive();
  }

  private static BpmnModelInstance createSimpleProcess(final String processId) {
    return Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
  }

  private static BpmnModelInstance createProcessWithServiceTask(
      final String processId, final String jobType) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .serviceTask("task")
        .zeebeJobType(jobType)
        .endEvent()
        .done();
  }
}
