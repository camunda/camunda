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
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.qa.util.multidb.MultiDbPhysicalTenants;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.qa.util.multidb.MultiPhysicalTenantClients;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@MultiDbTest
@MultiDbPhysicalTenants({"tenanta", "tenantb"})
final class MultiPhysicalTenantDocumentAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withAuthenticationMethod(AuthenticationMethod.BASIC);

  static MultiPhysicalTenantClients ptClients;

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String RESTRICTED_PASSWORD = "restricted";
  private static final Duration PROPAGATION_TIMEOUT = Duration.ofSeconds(30);

  @Test
  void shouldDenyDocumentDeleteWhenGrantExistsOnlyInAnotherPhysicalTenant() {
    final CamundaClient tenantAAdmin = ptClients.admin(TENANT_A);
    final String username = createUserWithForeignTenantGrant("doc-del", PermissionType.DELETE);

    final var documentReference =
        tenantAAdmin.newCreateDocumentCommand().content("tenant-a-only-content").send().join();

    try (final CamundaClient restrictedInA = restrictedClient(TENANT_A, username)) {
      awaitForbidden(
          "tenantb's own DOCUMENT:DELETE grant for this identity must not authorize a delete via"
              + " tenanta -- only tenanta's own authorization data may",
          () -> restrictedInA.newDeleteDocumentCommand(documentReference).send().join());

      // positive control -- granting DELETE in tenanta's own storage flips the same request to
      // allowed, proving the prior denial wasn't vacuous.
      grant(tenantAAdmin, username, PermissionType.DELETE);
      Awaitility.await("granting DELETE in tenanta itself authorizes the delete")
          .atMost(PROPAGATION_TIMEOUT)
          .ignoreExceptions()
          .untilAsserted(
              () -> restrictedInA.newDeleteDocumentCommand(documentReference).send().join());
    }

    // then: the document is actually gone from tenanta once deleted through it.
    final var exception =
        (ProblemException)
            assertThatThrownBy(
                    () ->
                        tenantAAdmin.newDocumentContentGetRequest(documentReference).send().join())
                .isInstanceOf(ProblemException.class)
                .actual();
    assertThat(exception.details().getStatus()).isEqualTo(404);
  }

  @Test
  void shouldDenyDocumentCreateWhenGrantExistsOnlyInAnotherPhysicalTenant() {
    final CamundaClient tenantAAdmin = ptClients.admin(TENANT_A);
    final String username = createUserWithForeignTenantGrant("doc-create", PermissionType.CREATE);

    try (final CamundaClient restrictedInA = restrictedClient(TENANT_A, username)) {
      awaitForbidden(
          "tenantb's own DOCUMENT:CREATE grant for this identity must not authorize creating a"
              + " document via tenanta -- only tenanta's own authorization data may",
          () ->
              restrictedInA
                  .newCreateDocumentCommand()
                  .content("should-not-be-creatable")
                  .send()
                  .join());

      // positive control -- granting CREATE in tenanta's own storage flips the same request to
      // allowed, proving the prior denial wasn't vacuous.
      grant(tenantAAdmin, username, PermissionType.CREATE);
      final var createdDocument = new AtomicReference<DocumentReferenceResponse>();
      Awaitility.await("granting CREATE in tenanta itself authorizes document creation")
          .atMost(PROPAGATION_TIMEOUT)
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  createdDocument.set(
                      restrictedInA
                          .newCreateDocumentCommand()
                          .content("created-once-granted-in-tenant-a")
                          .send()
                          .join()));

      // then: the document genuinely exists in tenanta's own store.
      assertReadableContent(
          tenantAAdmin, createdDocument.get(), "created-once-granted-in-tenant-a");
    }
  }

  @Test
  void shouldDenyDocumentContentGetWhenGrantExistsOnlyInAnotherPhysicalTenant() {
    final CamundaClient tenantAAdmin = ptClients.admin(TENANT_A);
    final String username = createUserWithForeignTenantGrant("doc-read", PermissionType.READ);

    final var documentReference =
        tenantAAdmin.newCreateDocumentCommand().content("tenant-a-secret-content").send().join();

    try (final CamundaClient restrictedInA = restrictedClient(TENANT_A, username)) {
      awaitForbidden(
          "tenantb's own DOCUMENT:READ grant for this identity must not authorize reading a"
              + " tenanta document",
          () -> restrictedInA.newDocumentContentGetRequest(documentReference).send().join());

      // positive control -- granting READ in tenanta's own storage flips the same request to
      // allowed, proving the prior denial wasn't vacuous.
      grant(tenantAAdmin, username, PermissionType.READ);
      Awaitility.await("granting READ in tenanta itself authorizes reading the document")
          .atMost(PROPAGATION_TIMEOUT)
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  assertReadableContent(
                      restrictedInA, documentReference, "tenant-a-secret-content"));
    }
  }

  private String createUserWithForeignTenantGrant(
      final String usernamePrefix, final PermissionType permission) {
    final CamundaClient tenantAAdmin = ptClients.admin(TENANT_A);
    final CamundaClient tenantBAdmin = ptClients.admin(TENANT_B);
    final String username = usernamePrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    createRestrictedUserNamed(tenantAAdmin, username);
    createRestrictedUserNamed(tenantBAdmin, username);
    grant(tenantBAdmin, username, permission);
    return username;
  }

  private static void awaitForbidden(final String reason, final ThrowingCallable operation) {
    Awaitility.await(reason)
        .during(PROPAGATION_TIMEOUT.dividedBy(6))
        .atMost(PROPAGATION_TIMEOUT)
        .untilAsserted(
            () ->
                assertThatThrownBy(operation)
                    .isInstanceOf(ProblemException.class)
                    .hasMessageContaining("status: 403")
                    .hasMessageContaining("FORBIDDEN"));
  }

  private static void assertReadableContent(
      final CamundaClient client,
      final DocumentReferenceResponse documentReference,
      final String expectedContent) {
    try (final var content = client.newDocumentContentGetRequest(documentReference).send().join()) {
      assertThat(new String(content.readAllBytes())).isEqualTo(expectedContent);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private CamundaClient restrictedClient(final String tenantId, final String username) {
    final String base = BROKER.restAddress().toString().replaceAll("/+$", "");
    final java.net.URI restAddress = java.net.URI.create(base + "/physical-tenants/" + tenantId);
    return BROKER
        .newClientBuilder()
        .physicalTenantId(tenantId)
        .preferRestOverGrpc(true)
        // the REST address already carries the /physical-tenants/<id> prefix, so opt out of the
        // client's auto-prefixing to avoid a doubled path
        .prefixPhysicalTenantPath(false)
        .restAddress(restAddress)
        .grpcAddress(BROKER.grpcAddress())
        .credentialsProvider(
            new BasicAuthCredentialsProviderBuilder()
                .applyEnvironmentOverrides(false)
                .username(username)
                .password(RESTRICTED_PASSWORD)
                .build())
        .build();
  }

  private static void createRestrictedUserNamed(final CamundaClient admin, final String username) {
    admin
        .newCreateUserCommand()
        .username(username)
        .password(RESTRICTED_PASSWORD)
        .name(username)
        .email(username + "@example.com")
        .send()
        .join();
    Awaitility.await("restricted user '" + username + "' exists in its PT")
        .atMost(PROPAGATION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        admin
                            .newUsersSearchRequest()
                            .filter(f -> f.username(username))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));
  }

  private static void grant(
      final CamundaClient admin, final String username, final PermissionType permission) {
    admin
        .newCreateAuthorizationCommand()
        .ownerId(username)
        .ownerType(OwnerType.USER)
        .resourceId("*")
        .resourceType(ResourceType.DOCUMENT)
        .permissionTypes(permission)
        .send()
        .join();
  }
}
