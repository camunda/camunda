/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import io.atomix.cluster.MemberId;
import io.camunda.configuration.physicaltenants.PhysicalTenantEnvironments;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.broker.PhysicalTenantConfigRejectedException;
import io.camunda.zeebe.broker.PhysicalTenantConfigService;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.io.TempDir;
import org.keycloak.json.RawJsonValue;
import org.keycloak.representations.idm.ClientPoliciesRepresentation;
import org.keycloak.representations.idm.ClientPolicyConditionRepresentation;
import org.keycloak.representations.idm.ClientPolicyExecutorRepresentation;
import org.keycloak.representations.idm.ClientPolicyRepresentation;
import org.keycloak.representations.idm.ClientProfileRepresentation;
import org.keycloak.representations.idm.ClientProfilesRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Changes the OIDC security of physical tenants through the {@code physicalTenants} actuator of a
 * cluster of three brokers with embedded gateways, and checks every change over real HTTP on the
 * REST endpoint of every broker, against identity providers in Keycloak. A Toxiproxy in front of
 * Keycloak makes an identity provider unreachable on demand.
 *
 * <p>The cluster's root authentication is OIDC, and every tenant has no secondary storage, as a
 * tenant added at runtime must. A boot tenant serves continuous REST load for the whole class, and
 * every request of it must succeed.
 */
@Testcontainers
@ZeebeIntegration
final class PhysicalTenantRuntimeSecurityIT {

  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantRuntimeSecurityIT.class);

  private static final Duration TIMEOUT = Duration.ofMinutes(2);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String BOOT_TENANT = "boottenant";
  private static final String ROOT_REALM = "root";
  private static final String BOOT_REALM = "boot";
  private static final String DEFAULT_TENANT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
  private static final String DEFAULT_ALIAS = "/physical-tenants/" + DEFAULT_TENANT;
  private static final String KEYCLOAK_ALIAS = "keycloak";
  private static final String PASSWORD = "password";
  private static final List<String> TRAFFIC_METERS =
      List.of("zeebe.messaging.", "http.server.requests", "zeebe.gateway.failed.requests");
  private static final List<String> POOLED_THREADS =
      List.of("api-service-thread-", "grpc-default-worker", "HttpClient-", "process reaper");
  private static final Network NETWORK = Network.newNetwork();
  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @Container
  private static final KeycloakContainer KEYCLOAK =
      DefaultTestContainers.createDefaultKeycloak()
          .withFeaturesEnabled("client-secret-rotation")
          .withNetwork(NETWORK)
          .withNetworkAliases(KEYCLOAK_ALIAS);

  @Container
  private static final ToxiproxyContainer TOXIPROXY =
      new ToxiproxyContainer(DockerImageName.parse("shopify/toxiproxy:2.1.0")).withNetwork(NETWORK);

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(BOOT_TENANT, Storage.none())
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestCluster CLUSTER =
      TestCluster.builder()
          .withBrokersCount(3)
          .withReplicationFactor(3)
          .withPartitionsCount(1)
          .withBrokerConfig(
              broker ->
                  TENANTS.configure(
                      broker
                          .withAuthenticatedAccess()
                          .withAuthenticationMethod(AuthenticationMethod.OIDC)))
          .build();

  @TempDir private static Path secrets;

  private static Path internalSecrets;
  private static Realm bootRealm;
  private static Realm defaultRealm;
  private static Proxy idpProxy;
  private static RestLoad bootLoad;

  @BeforeAll
  static void startCluster() throws IOException {
    final var jobSecrets = Files.createDirectory(secrets.resolve("jobs"));
    internalSecrets = Files.createDirectory(secrets.resolve("internal"));
    idpProxy =
        new ToxiproxyClient(TOXIPROXY.getHost(), TOXIPROXY.getControlPort())
            .createProxy("idp", "0.0.0.0:8666", KEYCLOAK_ALIAS + ":8080");

    final var root = Realm.create(ROOT_REALM, directIssuer(ROOT_REALM));
    final var boot = Realm.create(BOOT_REALM, directIssuer(BOOT_REALM));
    bootRealm = boot;
    defaultRealm = root;
    writeSecret(BOOT_TENANT + "-web", boot.webSecret());
    CLUSTER
        .brokers()
        .values()
        .forEach(
            broker -> {
              broker
                  .withProperty("camunda.secrets.stores.file.default.path", jobSecrets.toString())
                  .withProperty(
                      "camunda.secrets.stores.file.default.internal-config.path",
                      internalSecrets.toString())
                  .withProperty("camunda.secrets.refresh-interval", "1s")
                  .withSecurityConfig(
                      security -> {
                        final var oidc = security.getAuthentication().getOidc();
                        oidc.setIssuerUri(root.issuer());
                        oidc.setClientId(Realm.WEB_CLIENT);
                        oidc.setClientSecret(root.webSecret());
                        oidc.setRedirectUri("{baseUrl}/sso-callback");
                        oidc.setUsernameClaim("preferred_username");
                      });
              broker
                  .withProperty(
                      "camunda.physical-tenants.default.security.authorizations.enabled", "true")
                  .withProperty(
                      "camunda.physical-tenants.default.security.initialization.default-roles"
                          + ".admin.clients[0]",
                      Realm.SERVICE_CLIENT)
                  .withProperty(
                      "camunda.physical-tenants.default.security.initialization.default-roles"
                          + ".admin.clients[1]",
                      Realm.ADMIN_BY_CLAIM);
              if (broker
                  .unifiedConfig()
                  .getData()
                  .getExporters()
                  .containsKey(TestStandaloneBroker.RECORDING_EXPORTER_ID)) {
                broker.withProperty(
                    "camunda.physical-tenants.default.data.exporters-assigned[0]",
                    TestStandaloneBroker.RECORDING_EXPORTER_ID);
              } else {
                broker.withProperty("camunda.physical-tenants.default.data.exporters-assigned", "");
              }
              tenantSecurity(BOOT_TENANT, boot, "azp")
                  .forEach(
                      (name, value) ->
                          broker.withProperty(
                              "camunda.physical-tenants." + BOOT_TENANT + "." + name, value));
            });
    CLUSTER.start();
    Awaitility.await("every broker serves the boot tenant")
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                CLUSTER
                    .brokers()
                    .values()
                    .forEach(
                        broker ->
                            assertThat(
                                    rest(
                                        broker,
                                        BOOT_TENANT,
                                        boot.serviceToken(Realm.SERVICE_CLIENT)))
                                .isEqualTo(200)));
    bootLoad = new RestLoad(BOOT_TENANT, () -> boot.serviceToken(Realm.SERVICE_CLIENT));
    bootLoad.start();
  }

  @AfterAll
  static void stopLoad() throws InterruptedException {
    if (bootLoad != null) {
      bootLoad.stop();
      LOG.info(
          "REST load on '{}' during the whole class: {} requests, {} failed {}",
          BOOT_TENANT,
          bootLoad.succeeded() + bootLoad.failed(),
          bootLoad.failed(),
          bootLoad.failures());
      assertThat(bootLoad.failures()).isEmpty();
      assertThat(bootLoad.succeeded()).isPositive();
    }
  }

  @Test
  void shouldServeAnOidcTenantAddedAtRuntimeOnEveryBroker() {
    // given
    final var tenantId = uniqueTenantId();
    final var realm = Realm.create(tenantId, directIssuer(tenantId));
    final var otherRealm = Realm.create(tenantId + "x", directIssuer(tenantId + "x"));
    writeSecret(tenantId + "-web", realm.webSecret());
    final var token = realm.serviceToken(Realm.SERVICE_CLIENT);
    assertOnEveryBroker(tenantId, token, 404);

    // when
    final var started = System.nanoTime();
    assertThat(send("POST", broker(1), tenantId, tenantConfiguration(tenantId, realm, "azp")))
        .extracting(HttpResponse::statusCode)
        .isEqualTo(202);
    awaitOnEveryBroker(tenantId, token, 200);
    final var effective = Duration.ofNanos(System.nanoTime() - started);

    // then
    LOG.info(
        "Added OIDC tenant '{}' served on every broker {} ms after the call",
        tenantId,
        effective.toMillis());
    assertOnEveryBroker(tenantId, null, 401);
    assertOnEveryBroker(tenantId, otherRealm.serviceToken(Realm.SERVICE_CLIENT), 401);
    assertOnEveryBroker(BOOT_TENANT, token, 401);
    CLUSTER
        .brokers()
        .values()
        .forEach(
            broker ->
                assertThat(localSecurity(broker, tenantId).path("outcome").asText())
                    .isEqualTo("ADDED"));
  }

  @Test
  void shouldApplyAChangedIdentityProviderAndClaimsMappingOnEveryBroker() throws Exception {
    // given
    final var tenantId = uniqueTenantId();
    final var before = Realm.create(tenantId, directIssuer(tenantId));
    final var after = Realm.create(tenantId + "b", directIssuer(tenantId + "b"), "web-after", 300);
    writeSecret(tenantId + "-web", before.webSecret());
    create(tenantId, tenantConfiguration(tenantId, before, "azp"));
    final var beforeToken = before.serviceToken(Realm.SERVICE_CLIENT);
    awaitOnEveryBroker(tenantId, beforeToken, 200);
    awaitPublishedOnEveryBroker(tenantId, beforeToken);
    assertThat(publish(broker(2), tenantId, before.serviceToken(Realm.OTHER_SERVICE_CLIENT)))
        .isEqualTo(403);
    final var tenantSession = login(broker(0), tenantId, before);
    final var bootSession = login(broker(0), BOOT_TENANT, bootRealm);
    assertThat(tenantSession.topology()).isEqualTo(200);
    assertThat(bootSession.topology()).isEqualTo(200);
    writeSecret(tenantId + "-web", after.webSecret());
    final var afterAdminByClaim = after.serviceToken(Realm.OTHER_SERVICE_CLIENT);

    // when
    final var started = System.nanoTime();
    assertThat(send("PUT", broker(1), tenantId, tenantConfiguration(tenantId, after, "alt_client")))
        .extracting(HttpResponse::statusCode)
        .isEqualTo(202);
    awaitOnEveryBroker(tenantId, afterAdminByClaim, 200);
    final var effective = Duration.ofNanos(System.nanoTime() - started);
    awaitRolledOut(tenantId, 2);

    // then
    LOG.info(
        "Changed the identity provider of '{}'; new tokens accepted on every broker {} ms after"
            + " the call",
        tenantId,
        effective.toMillis());
    assertOnEveryBroker(tenantId, beforeToken, 401);
    awaitPublishedOnEveryBroker(tenantId, afterAdminByClaim);
    for (final var broker : CLUSTER.brokers().values()) {
      assertThat(publish(broker, tenantId, after.serviceToken(Realm.SERVICE_CLIENT)))
          .isEqualTo(403);
      assertThat(localSecurity(broker, tenantId).path("outcome").asText()).isEqualTo("REPLACED");
    }
    assertThat(tenantSession.topology()).isEqualTo(401);
    assertThat(bootSession.topology()).isEqualTo(200);
    assertThat(login(broker(0), tenantId, after).topology()).isEqualTo(200);
  }

  @Test
  void shouldAnswerNotFoundForTheWholeBasePathOfARemovedTenantOnEveryBroker() throws Exception {
    // given
    final var tenantId = uniqueTenantId();
    final var realm = Realm.create(tenantId, directIssuer(tenantId));
    writeSecret(tenantId + "-web", realm.webSecret());
    create(tenantId, tenantConfiguration(tenantId, realm, "azp"));
    final var token = realm.serviceToken(Realm.SERVICE_CLIENT);
    awaitOnEveryBroker(tenantId, token, 200);
    final var session = login(broker(0), tenantId, realm);
    assertThat(session.topology()).isEqualTo(200);

    // when
    final var started = System.nanoTime();
    assertThat(send("DELETE", broker(2), tenantId, null))
        .extracting(HttpResponse::statusCode)
        .isEqualTo(202);
    awaitOnEveryBroker(tenantId, token, 404);
    final var effective = Duration.ofNanos(System.nanoTime() - started);

    // then
    LOG.info(
        "Removed '{}'; 404 on every broker {} ms after the call", tenantId, effective.toMillis());
    assertThat(session.topology()).isEqualTo(404);
    for (final var broker : CLUSTER.brokers().values()) {
      for (final var path :
          List.of(
              "/v2/topology",
              "/v2/messages/publication",
              "/oauth2/authorization/oidc",
              "/login",
              "/sso-callback",
              "/operate")) {
        assertThat(
                request(broker, "GET", "/physical-tenants/" + tenantId + path, token, null)
                    .statusCode())
            .as("GET %s of the removed tenant", path)
            .isEqualTo(404);
      }
    }
    Awaitility.await("the removal completes")
        .atMost(TIMEOUT)
        .until(
            () ->
                send("GET", broker(0), tenantId, null).statusCode() == 404
                    || status(broker(0), tenantId).path("state").asText().equals("REMOVED"));
    final var again =
        send("POST", broker(0), tenantId, tenantConfiguration(tenantId, realm, "azp"));
    assertThat(again.statusCode()).isEqualTo(400);
    assertThat(again.body()).contains("stays reserved");
    assertOnEveryBroker(tenantId, token, 404);
  }

  @Test
  void shouldRejectAChangeOfATenantsAuthenticationMethod() {
    // given
    final var tenantId = uniqueTenantId();
    final var realm = Realm.create(tenantId, directIssuer(tenantId));
    writeSecret(tenantId + "-web", realm.webSecret());
    create(tenantId, tenantConfiguration(tenantId, realm, "azp"));
    final var basic = tenantConfiguration(tenantId, realm, "azp");
    basic.put("security.authentication.method", "basic");

    // when
    final var response = send("PUT", broker(0), tenantId, basic);

    // then
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("security.authentication.method");
    assertThat(status(broker(0), tenantId).path("version").asLong()).isEqualTo(1);
    awaitOnEveryBroker(tenantId, realm.serviceToken(Realm.SERVICE_CLIENT), 200);
  }

  @Test
  void shouldKeepThePreviousSecurityWhileTheNewIdentityProviderIsUnreachable() throws Exception {
    // given
    final var tenantId = uniqueTenantId();
    final var before = Realm.create(tenantId, directIssuer(tenantId));
    final var after = Realm.create(tenantId + "b", proxiedIssuer(tenantId + "b"));
    writeSecret(tenantId + "-web", before.webSecret());
    create(tenantId, tenantConfiguration(tenantId, before, "azp"));
    final var beforeToken = before.serviceToken(Realm.SERVICE_CLIENT);
    awaitOnEveryBroker(tenantId, beforeToken, 200);
    final var afterToken = after.serviceToken(Realm.SERVICE_CLIENT);
    idpProxy.disable();
    try {
      // when
      assertThat(send("PUT", broker(0), tenantId, tenantConfiguration(tenantId, after, "azp")))
          .extracting(HttpResponse::statusCode)
          .isEqualTo(202);

      // then
      Awaitility.await("the first member keeps the previous security and reports it")
          .atMost(TIMEOUT)
          .untilAsserted(
              () -> {
                final var local = status(broker(0), tenantId).path("local");
                assertThat(local.path("health").asText()).isEqualTo("DEGRADED");
                assertThat(local.path("healthDetail").asText())
                    .contains("keeps its previous HTTP security", "KEPT_PREVIOUS");
                assertThat(local.path("httpSecurity").path("outcome").asText())
                    .isEqualTo("KEPT_PREVIOUS");
              });
      final var status = status(broker(0), tenantId);
      assertThat(status.path("rolledOut").asBoolean()).isFalse();
      assertThat(status.path("version").asLong()).isEqualTo(2);
      assertOnEveryBroker(tenantId, beforeToken, 200);
      assertOnEveryBroker(tenantId, afterToken, 401);
      CLUSTER
          .brokers()
          .values()
          .forEach(
              broker ->
                  assertThat(
                          broker
                              .bean(io.camunda.cluster.PhysicalTenantRegistry.class)
                              .health(BOOT_TENANT)
                              .orElseThrow()
                              .state())
                      .isEqualTo(io.camunda.cluster.PhysicalTenantHealth.State.HEALTHY));
    } finally {
      idpProxy.enable();
    }

    // and when the identity provider answers again
    final var recovered = System.nanoTime();
    awaitRolledOut(tenantId, 2);
    awaitOnEveryBroker(tenantId, afterToken, 200);
    LOG.info(
        "'{}' applied its new identity provider on every broker {} ms after it became reachable",
        tenantId,
        Duration.ofNanos(System.nanoTime() - recovered).toMillis());
    assertOnEveryBroker(tenantId, beforeToken, 401);
    Awaitility.await("the tenant is healthy again")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                CLUSTER
                    .brokers()
                    .values()
                    .forEach(
                        broker ->
                            assertThat(
                                    status(broker, tenantId).path("local").path("health").asText())
                                .isEqualTo("HEALTHY")));
  }

  @Test
  void shouldRotateTheClientSecretFromTheSecretStoreAndKeepTheSessions() throws Exception {
    // given
    final var tenantId = uniqueTenantId();
    final var realm = Realm.create(tenantId, directIssuer(tenantId), Realm.WEB_CLIENT, 5);
    writeSecret(tenantId + "-web", realm.webSecret());
    create(tenantId, tenantConfiguration(tenantId, realm, "azp"));
    awaitOnEveryBroker(tenantId, realm.serviceToken(Realm.SERVICE_CLIENT), 200);
    final var modules = moduleContexts(tenantId);
    final var session = login(broker(0), tenantId, realm);
    final var bootSession = login(broker(0), BOOT_TENANT, bootRealm);
    assertThat(session.topology()).isEqualTo(200);
    final var rotated = realm.rotateWebSecret();
    assertThat(realm.acceptsWebSecret(realm.webSecret())).isTrue();
    assertThat(realm.acceptsWebSecret(rotated)).isTrue();

    // when
    final var started = System.nanoTime();
    writeSecret(tenantId + "-web", rotated);
    awaitSecurityOutcomeOnEveryBroker(tenantId, "CREDENTIALS_ROTATED");
    final var effective = Duration.ofNanos(System.nanoTime() - started);
    realm.revokeRotatedWebSecret();
    final var revokedAt = java.time.Instant.now();

    // then
    LOG.info(
        "Rotated the client secret of '{}' on every broker {} ms after the store changed",
        tenantId,
        effective.toMillis());
    assertThat(realm.acceptsWebSecret(realm.webSecret())).isFalse();
    Awaitility.await("the session's access token expired, so its next request refreshes it")
        .atMost(Duration.ofSeconds(30))
        .until(() -> java.time.Instant.now().isAfter(revokedAt.plusSeconds(6)));
    assertThat(session.topology()).isEqualTo(200);
    assertThat(login(broker(1), tenantId, realm).topology()).isEqualTo(200);
    assertThat(bootSession.topology()).isEqualTo(200);
    assertThat(moduleContexts(tenantId)).isEqualTo(modules);
    assertThat(status(broker(0), tenantId).path("version").asLong()).isEqualTo(1);
    assertOnEveryBroker(tenantId, realm.serviceToken(Realm.SERVICE_CLIENT), 200);
  }

  @Test
  void shouldRotateTheAssertionKeystoreFromTheSecretStoreAndKeepTheSessions() throws Exception {
    // given
    final var tenantId = uniqueTenantId();
    final var first = Keystore.generate(secrets.resolve(tenantId + "-first.p12"), "first-pw");
    final var keystorePath = secrets.resolve(tenantId + ".p12");
    Files.copy(first.path(), keystorePath);
    final var realm = Realm.create(tenantId, directIssuer(tenantId), Realm.WEB_CLIENT, 5);
    final var second = Keystore.generate(secrets.resolve(tenantId + "-second.p12"), "first-pw");
    final var third = Keystore.generate(secrets.resolve(tenantId + "-third.p12"), "third-pw");
    realm.useSignedJwtForWebClient(List.of(first, second));
    writeSecret(tenantId + "-keystore-path", keystorePath.toString());
    writeSecret(tenantId + "-keystore-password", first.password());
    final var configuration = tenantConfiguration(tenantId, realm, "azp");
    configuration.remove("security.authentication.oidc.client-secret");
    final var oidc = "security.authentication.oidc.";
    configuration.put(oidc + "client-authentication-method", "private_key_jwt");
    configuration.put(
        oidc + "assertion.keystore.path", "camunda.secrets." + tenantId + "-keystore-path");
    configuration.put(
        oidc + "assertion.keystore.password", "camunda.secrets." + tenantId + "-keystore-password");
    configuration.put(oidc + "assertion.keystore.key-alias", Keystore.ALIAS);
    configuration.put(
        oidc + "assertion.keystore.key-password",
        "camunda.secrets." + tenantId + "-keystore-password");
    create(tenantId, configuration);
    final var session = login(broker(0), tenantId, realm);
    assertThat(session.topology()).isEqualTo(200);

    // when the keystore file is replaced at the same path
    Files.copy(second.path(), keystorePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    awaitSecurityOutcomeOnEveryBroker(tenantId, "CREDENTIALS_REFRESHED");
    realm.useSignedJwtForWebClient(List.of(second, third));
    final var refreshedAt = java.time.Instant.now();

    // then the tenant signs with the new key and its session continues
    Awaitility.await("the session's access token expired")
        .atMost(Duration.ofSeconds(30))
        .until(() -> java.time.Instant.now().isAfter(refreshedAt.plusSeconds(6)));
    assertThat(session.topology()).isEqualTo(200);
    assertThat(login(broker(2), tenantId, realm).topology()).isEqualTo(200);

    // when the store points at a new keystore with a new password
    writeSecret(tenantId + "-keystore-path", third.path().toString());
    writeSecret(tenantId + "-keystore-password", third.password());
    awaitSecurityOutcomeOnEveryBroker(tenantId, "CREDENTIALS_ROTATED");
    realm.useSignedJwtForWebClient(List.of(third));
    final var rotatedAt = java.time.Instant.now();

    // then
    Awaitility.await("the session's access token expired")
        .atMost(Duration.ofSeconds(30))
        .until(() -> java.time.Instant.now().isAfter(rotatedAt.plusSeconds(6)));
    assertThat(session.topology()).isEqualTo(200);
    assertThat(login(broker(1), tenantId, realm).topology()).isEqualTo(200);
    assertThat(status(broker(0), tenantId).path("version").asLong()).isEqualTo(1);
  }

  @Test
  void shouldNotLeakOverManySecurityChangesOfOneTenant() throws Exception {
    // given
    final var tenantId = uniqueTenantId();
    final var realms =
        List.of(
            Realm.create(tenantId, directIssuer(tenantId)),
            Realm.create(tenantId + "b", directIssuer(tenantId + "b")));
    writeSecret(tenantId + "-web", realms.get(0).webSecret());
    create(tenantId, tenantConfiguration(tenantId, realms.get(0), "azp"));
    var version = 1L;
    for (int i = 0; i < 2; i++) {
      version = change(tenantId, realms.get((int) version % 2), version);
    }
    final var before = SecurityResources.measure();
    final List<Long> durations = new ArrayList<>();

    // when
    for (int i = 0; i < 10; i++) {
      final var started = System.nanoTime();
      version = change(tenantId, realms.get((int) version % 2), version);
      durations.add(Duration.ofNanos(System.nanoTime() - started).toMillis());
    }
    final var after = SecurityResources.measure();

    // then
    LOG.info(
        "10 security changes of '{}', each new IdP accepted on every broker after {} ms;"
            + " before {}, after {}",
        tenantId,
        durations,
        before,
        after);
    assertThat(after.chains()).isEqualTo(before.chains());
    assertThat(after.bearerConverters()).isLessThanOrEqualTo(before.bearerConverters());
    assertThat(after.claimsConverters()).isLessThanOrEqualTo(before.claimsConverters());
    assertThat(after.threads())
        .as("threads before %s, after %s", before.threadNames(), after.threadNames())
        .isLessThanOrEqualTo(before.threads());
    assertThat(after.added(before)).as("meters added by the changes").isEmpty();
  }

  @Test
  void shouldChangeTheDefaultTenantsIdentityProviderAndClaimsMappingOnEveryBroker()
      throws Exception {
    // given
    final var suffix = uniqueTenantId();
    final var before = Realm.create("c" + suffix, directIssuer("c" + suffix));
    changeDefaultAndAwait(before, "default-web-c" + suffix);
    final var after = Realm.create("d" + suffix, directIssuer("d" + suffix), "web-after", 300);
    final var secret = "default-web-" + suffix;
    writeSecret(secret, after.webSecret());
    final var beforeToken = before.serviceToken(Realm.SERVICE_CLIENT);
    awaitDefaultOnEveryBroker(beforeToken, 200);
    final var unprefixedSession = loginAt(broker(0), "", before);
    final var aliasSession = loginAt(broker(0), DEFAULT_ALIAS, before);
    final var bootSession = login(broker(0), BOOT_TENANT, bootRealm);
    assertThat(unprefixedSession.topology()).isEqualTo(200);
    assertThat(aliasSession.topology()).isEqualTo(200);
    for (final var prefix : List.of("", DEFAULT_ALIAS)) {
      assertThat(publishAt(broker(2), prefix, beforeToken)).isEqualTo(200);
      assertThat(publishAt(broker(2), prefix, before.serviceToken(Realm.OTHER_SERVICE_CLIENT)))
          .isEqualTo(403);
    }
    final var bootGenerations = generations(BOOT_TENANT);
    final var version = status(broker(0), DEFAULT_TENANT).path("version").asLong();
    final var afterAdminByClaim = after.serviceToken(Realm.OTHER_SERVICE_CLIENT);

    // when
    final var started = System.nanoTime();
    changeDefault(broker(1), defaultSubtree(after, "alt_client", secret));
    awaitDefaultOnEveryBroker(afterAdminByClaim, 200);
    final var effective = Duration.ofNanos(System.nanoTime() - started);
    awaitRolledOut(DEFAULT_TENANT, version + 1);
    defaultRealm = after;

    // then
    LOG.info(
        "Changed the identity provider of the default tenant; new tokens accepted on /v2 and its"
            + " alias on every broker {} ms after the call",
        effective.toMillis());
    assertDefaultOnEveryBroker(beforeToken, 401);
    for (final var broker : CLUSTER.brokers().values()) {
      for (final var prefix : List.of("", DEFAULT_ALIAS)) {
        assertThat(publishAt(broker, prefix, afterAdminByClaim))
            .as("publish on '%s' as the admin by claim", prefix)
            .isEqualTo(200);
        assertThat(publishAt(broker, prefix, after.serviceToken(Realm.SERVICE_CLIENT)))
            .as("publish on '%s' as svc, no admin by the new claim", prefix)
            .isEqualTo(403);
      }
      assertThat(localSecurity(broker, DEFAULT_TENANT).path("outcome").asText())
          .isEqualTo("REPLACED");
    }
    assertThat(unprefixedSession.topology()).isEqualTo(401);
    assertThat(aliasSession.topology()).isEqualTo(401);
    assertThat(bootSession.topology()).isEqualTo(200);
    assertThat(loginAt(broker(0), "", after).topology()).isEqualTo(200);
    assertThat(loginAt(broker(2), DEFAULT_ALIAS, after).topology()).isEqualTo(200);
    assertThat(generations(BOOT_TENANT)).isEqualTo(bootGenerations);
    assertOnEveryBroker(BOOT_TENANT, bootRealm.serviceToken(Realm.SERVICE_CLIENT), 200);
    assertOnEveryBroker(BOOT_TENANT, afterAdminByClaim, 401);
  }

  @Test
  void shouldRotateTheDefaultTenantsClientSecretFromTheSecretStoreAndKeepTheSessions()
      throws Exception {
    // given
    final var suffix = uniqueTenantId();
    final var realm = Realm.create("d" + suffix, directIssuer("d" + suffix), Realm.WEB_CLIENT, 5);
    final var secret = "default-web-" + suffix;
    writeSecret(secret, realm.webSecret());
    final var version = status(broker(0), DEFAULT_TENANT).path("version").asLong();
    changeDefault(broker(0), defaultSubtree(realm, "azp", secret));
    awaitDefaultOnEveryBroker(realm.serviceToken(Realm.SERVICE_CLIENT), 200);
    awaitRolledOut(DEFAULT_TENANT, version + 1);
    defaultRealm = realm;
    final var unprefixedSession = loginAt(broker(0), "", realm);
    final var aliasSession = loginAt(broker(1), DEFAULT_ALIAS, realm);
    assertThat(unprefixedSession.topology()).isEqualTo(200);
    assertThat(aliasSession.topology()).isEqualTo(200);
    final var rotated = realm.rotateWebSecret();

    // when
    final var started = System.nanoTime();
    writeSecret(secret, rotated);
    awaitSecurityOutcomeOnEveryBroker(DEFAULT_TENANT, "CREDENTIALS_ROTATED");
    final var effective = Duration.ofNanos(System.nanoTime() - started);
    realm.revokeRotatedWebSecret();
    final var revokedAt = java.time.Instant.now();

    // then
    LOG.info(
        "Rotated the client secret of the default tenant on every broker {} ms after the store"
            + " changed",
        effective.toMillis());
    assertThat(realm.acceptsWebSecret(realm.webSecret())).isFalse();
    Awaitility.await("the sessions' access tokens expired, so their next requests refresh them")
        .atMost(Duration.ofSeconds(30))
        .until(() -> java.time.Instant.now().isAfter(revokedAt.plusSeconds(6)));
    assertThat(unprefixedSession.topology()).isEqualTo(200);
    assertThat(aliasSession.topology()).isEqualTo(200);
    assertThat(loginAt(broker(2), "", realm).topology()).isEqualTo(200);
    assertThat(loginAt(broker(2), DEFAULT_ALIAS, realm).topology()).isEqualTo(200);
    assertThat(status(broker(0), DEFAULT_TENANT).path("version").asLong()).isEqualTo(version + 1);
  }

  @Test
  void shouldKeepTheDefaultTenantsPreviousSecurityWhileItsNewIdentityProviderIsUnreachable()
      throws Exception {
    // given
    final var suffix = uniqueTenantId();
    final var before = defaultRealm;
    final var after = Realm.create("d" + suffix, proxiedIssuer("d" + suffix));
    final var secret = "default-web-" + suffix;
    writeSecret(secret, after.webSecret());
    final var beforeToken = before.serviceToken(Realm.SERVICE_CLIENT);
    awaitDefaultOnEveryBroker(beforeToken, 200);
    final var afterToken = after.serviceToken(Realm.SERVICE_CLIENT);
    final var version = status(broker(0), DEFAULT_TENANT).path("version").asLong();
    idpProxy.disable();
    try {
      // when
      changeDefault(broker(0), defaultSubtree(after, "azp", secret));

      // then
      Awaitility.await("the first member keeps the previous security and reports it")
          .atMost(TIMEOUT)
          .untilAsserted(
              () -> {
                final var local = status(broker(0), DEFAULT_TENANT).path("local");
                assertThat(local.path("health").asText()).isEqualTo("DEGRADED");
                assertThat(local.path("healthDetail").asText()).contains("KEPT_PREVIOUS");
                assertThat(local.path("httpSecurity").path("outcome").asText())
                    .isEqualTo("KEPT_PREVIOUS");
              });
      assertThat(status(broker(0), DEFAULT_TENANT).path("rolledOut").asBoolean()).isFalse();
      assertDefaultOnEveryBroker(beforeToken, 200);
      assertDefaultOnEveryBroker(afterToken, 401);
      assertThat(
              broker(0)
                  .bean(io.camunda.cluster.PhysicalTenantRegistry.class)
                  .health(BOOT_TENANT)
                  .orElseThrow()
                  .state())
          .isEqualTo(io.camunda.cluster.PhysicalTenantHealth.State.HEALTHY);
    } finally {
      idpProxy.enable();
    }

    // and when the identity provider answers again
    final var recovered = System.nanoTime();
    awaitRolledOut(DEFAULT_TENANT, version + 1);
    awaitDefaultOnEveryBroker(afterToken, 200);
    defaultRealm = after;
    LOG.info(
        "The default tenant applied its new identity provider on every broker {} ms after it"
            + " became reachable",
        Duration.ofNanos(System.nanoTime() - recovered).toMillis());
    assertDefaultOnEveryBroker(beforeToken, 401);
    Awaitility.await("the default tenant is healthy again")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                CLUSTER
                    .brokers()
                    .values()
                    .forEach(
                        broker ->
                            assertThat(
                                    status(broker, DEFAULT_TENANT)
                                        .path("local")
                                        .path("health")
                                        .asText())
                                .isEqualTo("HEALTHY")));
  }

  @Test
  void shouldRejectAChangeOfTheDefaultTenantsAuthenticationMethod() {
    // given
    final var subtree = new LinkedHashMap<>(defaultBaseSubtree());
    subtree.put("security.authentication.method", "basic");
    final var version = status(broker(0), DEFAULT_TENANT).path("version").asLong();

    // when / then
    assertThatThrownBy(() -> changeDefault(broker(0), subtree))
        .isInstanceOf(PhysicalTenantConfigRejectedException.class)
        .satisfies(
            e ->
                assertThat(((PhysicalTenantConfigRejectedException) e).violations())
                    .anyMatch(violation -> violation.contains("security.authentication.method")));
    assertThat(status(broker(0), DEFAULT_TENANT).path("version").asLong()).isEqualTo(version);
    assertDefaultOnEveryBroker(defaultRealm.serviceToken(Realm.SERVICE_CLIENT), 200);
  }

  @Test
  void shouldNotLeakOverManySecurityChangesOfTheDefaultTenant() throws Exception {
    // given
    final var suffix = uniqueTenantId();
    final var realms =
        List.of(
            Realm.create("d" + suffix, directIssuer("d" + suffix)),
            Realm.create("e" + suffix, directIssuer("e" + suffix)));
    for (int i = 0; i < 2; i++) {
      changeDefaultAndAwait(realms.get(i % 2), "default-web-" + suffix + i);
    }
    final var before = SecurityResources.measure();
    final List<Long> durations = new ArrayList<>();

    // when
    for (int i = 0; i < 10; i++) {
      final var started = System.nanoTime();
      changeDefaultAndAwait(realms.get(i % 2), "default-web-" + suffix + i);
      durations.add(Duration.ofNanos(System.nanoTime() - started).toMillis());
    }
    final var after = SecurityResources.measure();

    // then
    LOG.info(
        "10 security changes of the default tenant, each new IdP accepted on /v2 and its alias on"
            + " every broker and rolled out after {} ms; before {}, after {}",
        durations,
        before,
        after);
    assertThat(after.chains()).isEqualTo(before.chains());
    assertThat(after.bearerConverters()).isLessThanOrEqualTo(before.bearerConverters());
    assertThat(after.claimsConverters()).isLessThanOrEqualTo(before.claimsConverters());
    assertThat(after.threads())
        .as("threads before %s, after %s", before.threadNames(), after.threadNames())
        .isLessThanOrEqualTo(before.threads());
    assertThat(after.added(before)).as("meters added by the changes").isEmpty();
  }

  private static void changeDefaultAndAwait(final Realm realm, final String secret) {
    writeSecret(secret, realm.webSecret());
    final var version = status(broker(0), DEFAULT_TENANT).path("version").asLong();
    changeDefault(broker((int) (version % 3)), defaultSubtree(realm, "azp", secret));
    awaitDefaultOnEveryBroker(realm.serviceToken(Realm.SERVICE_CLIENT), 200);
    awaitRolledOut(DEFAULT_TENANT, version + 1);
    defaultRealm = realm;
  }

  /**
   * Replaces the subtree of the default tenant as a changed static configuration file would; the
   * default tenant is owned by the file, so the management API refuses to change it.
   */
  private static void changeDefault(
      final TestStandaloneBroker broker, final Map<String, String> subtree) {
    broker.bean(PhysicalTenantConfigService.class).updateFromFile(DEFAULT_TENANT, subtree);
  }

  /** The subtree of the default tenant in the static configuration of the brokers. */
  private static Map<String, String> defaultBaseSubtree() {
    return defaultBaseSubtree(CLUSTER);
  }

  private static Map<String, String> defaultBaseSubtree(final TestCluster cluster) {
    return PhysicalTenantEnvironments.subtreeOf(
        first(cluster).bean(ConfigurableEnvironment.class), DEFAULT_TENANT);
  }

  private static Map<String, String> defaultSubtree(
      final Realm realm, final String clientIdClaim, final String secret) {
    return defaultSubtree(defaultBaseSubtree(), realm, clientIdClaim, secret);
  }

  private static Map<String, String> defaultSubtree(
      final Map<String, String> base,
      final Realm realm,
      final String clientIdClaim,
      final String secret) {
    final Map<String, String> subtree = new LinkedHashMap<>(base);
    final var oidc = "security.authentication.oidc.";
    subtree.put(oidc + "issuer-uri", realm.issuer());
    subtree.put(oidc + "client-id", realm.webClient());
    subtree.put(oidc + "client-secret", "camunda.secrets." + secret);
    subtree.put(oidc + "audiences[0]", realm.audience());
    subtree.put(oidc + "username-claim", "absent_username");
    subtree.put(oidc + "client-id-claim", clientIdClaim);
    subtree.put("security.authentication.providers.assigned[0]", "oidc");
    return subtree;
  }

  /** The generation of the tenant's HTTP security on every broker. */
  private static Map<MemberId, Long> generations(final String tenantId) {
    final Map<MemberId, Long> generations = new LinkedHashMap<>();
    CLUSTER
        .brokers()
        .forEach(
            (id, broker) ->
                generations.put(id, localSecurity(broker, tenantId).path("generation").asLong()));
    return generations;
  }

  /** Checks the default tenant on its unprefixed paths and on its alias, on every broker. */
  private static void assertDefaultOnEveryBroker(final String token, final int status) {
    assertDefaultOnEveryBroker(CLUSTER, token, status);
  }

  private static void assertDefaultOnEveryBroker(
      final TestCluster cluster, final String token, final int status) {
    cluster
        .brokers()
        .forEach(
            (id, broker) -> {
              for (final var prefix : List.of("", DEFAULT_ALIAS)) {
                assertThat(
                        request(broker, "GET", prefix + "/v2/topology", token, null).statusCode())
                    .as("GET %s/v2/topology on broker %s", prefix, id)
                    .isEqualTo(status);
              }
            });
  }

  private static void awaitDefaultOnEveryBroker(final String token, final int status) {
    awaitDefaultOnEveryBroker(CLUSTER, token, status);
  }

  private static void awaitDefaultOnEveryBroker(
      final TestCluster cluster, final String token, final int status) {
    Awaitility.await("the default tenant answers %d on every broker".formatted(status))
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(() -> assertDefaultOnEveryBroker(cluster, token, status));
  }

  private static int publishAt(
      final TestStandaloneBroker broker, final String prefix, final String token) {
    return request(
            broker,
            "POST",
            prefix + "/v2/messages/publication",
            token,
            "{\"name\":\"probe\",\"correlationKey\":\"" + UUID.randomUUID() + "\"}")
        .statusCode();
  }

  /**
   * A cluster that configures no physical tenant at all, so the default tenant is implicit: no key
   * under {@code camunda.physical-tenants.*} exists when it starts. Without a generic exporter its
   * security changes like that of the cluster above.
   */
  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  final class WithoutPhysicalTenants {

    private TestCluster cluster;
    private Realm startupRealm;

    @BeforeAll
    void startCluster() {
      final var suffix = uniqueTenantId();
      startupRealm = Realm.create("n" + suffix, directIssuer("n" + suffix));
      cluster = startDefaultOnlyCluster(startupRealm, false);
    }

    @AfterAll
    void stopCluster() {
      if (cluster != null) {
        cluster.close();
      }
    }

    @Test
    void shouldChangeTheImplicitDefaultTenantsSecurityOnEveryBroker() throws Exception {
      // given
      assertThat(defaultBaseSubtree(cluster)).as("no physical tenant is configured").isEmpty();
      final var startupToken = startupRealm.serviceToken(Realm.SERVICE_CLIENT);
      awaitDefaultOnEveryBroker(cluster, startupToken, 200);
      final var unprefixedSession = loginAt(first(cluster), "", startupRealm);
      assertThat(unprefixedSession.topology()).isEqualTo(200);
      final var suffix = uniqueTenantId();
      final var after = Realm.create("m" + suffix, directIssuer("m" + suffix));
      final var secret = "default-web-m" + suffix;
      writeSecret(secret, after.webSecret());
      final var version = status(first(cluster), DEFAULT_TENANT).path("version").asLong();

      // when
      final var started = System.nanoTime();
      last(cluster)
          .bean(PhysicalTenantConfigService.class)
          .updateFromFile(DEFAULT_TENANT, defaultSubtree(Map.of(), after, "azp", secret));
      awaitDefaultOnEveryBroker(cluster, after.serviceToken(Realm.SERVICE_CLIENT), 200);
      final var effective = Duration.ofNanos(System.nanoTime() - started);
      awaitRolledOut(first(cluster), DEFAULT_TENANT, version + 1);

      // then
      LOG.info(
          "Changed the identity provider of the implicit default tenant; new tokens accepted on"
              + " /v2 and its alias on every broker {} ms after the call",
          effective.toMillis());
      assertDefaultOnEveryBroker(cluster, startupToken, 401);
      assertThat(unprefixedSession.topology()).isEqualTo(401);
      assertThat(loginAt(last(cluster), "", after).topology()).isEqualTo(200);
      assertThat(loginAt(first(cluster), DEFAULT_ALIAS, after).topology()).isEqualTo(200);
    }
  }

  /**
   * As {@link WithoutPhysicalTenants}, with a generic exporter in the root configuration, as the
   * test clusters register the recording exporter.
   */
  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  final class WithoutPhysicalTenantsButAGenericExporter {

    private TestCluster cluster;
    private Realm startupRealm;

    @BeforeAll
    void startCluster() {
      final var suffix = uniqueTenantId();
      startupRealm = Realm.create("x" + suffix, directIssuer("x" + suffix));
      cluster = startDefaultOnlyCluster(startupRealm, true);
    }

    @AfterAll
    void stopCluster() {
      if (cluster != null) {
        cluster.close();
      }
    }

    @Test
    void shouldRejectTheFirstKeyOfTheImplicitDefaultTenant() {
      // given
      assertThat(defaultBaseSubtree(cluster)).as("no physical tenant is configured").isEmpty();
      final var startupToken = startupRealm.serviceToken(Realm.SERVICE_CLIENT);
      awaitDefaultOnEveryBroker(cluster, startupToken, 200);
      final var suffix = uniqueTenantId();
      final var after = Realm.create("y" + suffix, directIssuer("y" + suffix));
      final var secret = "default-web-y" + suffix;
      writeSecret(secret, after.webSecret());
      final var securityOnly = defaultSubtree(Map.of(), after, "azp", secret);
      final var withExporters = new LinkedHashMap<>(securityOnly);
      withExporters.put("data.exporters-assigned[0]", TestStandaloneBroker.RECORDING_EXPORTER_ID);
      final var service = first(cluster).bean(PhysicalTenantConfigService.class);

      // when / then
      assertThatThrownBy(() -> service.updateFromFile(DEFAULT_TENANT, securityOnly))
          .isInstanceOfSatisfying(
              PhysicalTenantConfigRejectedException.class,
              rejected -> {
                LOG.info("Security-only change of the implicit default: {}", rejected.violations());
                assertThat(String.join(" ", rejected.violations())).contains("exporters-assigned");
              });
      assertThatThrownBy(() -> service.updateFromFile(DEFAULT_TENANT, withExporters))
          .isInstanceOfSatisfying(
              PhysicalTenantConfigRejectedException.class,
              rejected -> {
                LOG.info("Change with exporters-assigned: {}", rejected.violations());
                assertThat(rejected.violations())
                    .anyMatch(violation -> violation.contains("exporters-assigned"));
              });
      assertDefaultOnEveryBroker(cluster, startupToken, 200);
    }
  }

  /**
   * Starts two brokers whose static configuration names no physical tenant, with the given realm as
   * their root identity provider.
   */
  private static TestCluster startDefaultOnlyCluster(
      final Realm realm, final boolean genericExporter) {
    final var tenants =
        PhysicalTenantsITHelper.builder()
            .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
            .build();
    final var cluster =
        TestCluster.builder()
            .withName("default-only-" + uniqueTenantId())
            .withBrokersCount(2)
            .withReplicationFactor(2)
            .withPartitionsCount(1)
            .useRecordingExporter(genericExporter)
            .withBrokerConfig(
                broker ->
                    tenants.configure(
                        broker
                            .withAuthenticatedAccess()
                            .withAuthenticationMethod(AuthenticationMethod.OIDC)))
            .build();
    cluster
        .brokers()
        .values()
        .forEach(
            broker ->
                broker
                    .withProperty(
                        "camunda.secrets.stores.file.default.path",
                        secrets.resolve("jobs").toString())
                    .withProperty(
                        "camunda.secrets.stores.file.default.internal-config.path",
                        internalSecrets.toString())
                    .withProperty("camunda.secrets.refresh-interval", "1s")
                    .withSecurityConfig(
                        security -> {
                          final var oidc = security.getAuthentication().getOidc();
                          oidc.setIssuerUri(realm.issuer());
                          oidc.setClientId(Realm.WEB_CLIENT);
                          oidc.setClientSecret(realm.webSecret());
                          oidc.setRedirectUri("{baseUrl}/sso-callback");
                          oidc.setUsernameClaim("preferred_username");
                        }));
    cluster.start();
    return cluster;
  }

  private static TestStandaloneBroker first(final TestCluster cluster) {
    return cluster.brokers().get(MemberId.from("0"));
  }

  private static TestStandaloneBroker last(final TestCluster cluster) {
    return cluster.brokers().get(MemberId.from(String.valueOf(cluster.brokers().size() - 1)));
  }

  private static long change(final String tenantId, final Realm realm, final long version) {
    writeSecret(tenantId + "-web", realm.webSecret());
    assertThat(
            send(
                    "PUT",
                    broker((int) (version % 3)),
                    tenantId,
                    tenantConfiguration(tenantId, realm, version % 2 == 0 ? "azp" : "alt_client"))
                .statusCode())
        .isEqualTo(202);
    awaitOnEveryBroker(tenantId, realm.serviceToken(Realm.SERVICE_CLIENT), 200);
    awaitRolledOut(tenantId, version + 1);
    final var admin =
        realm.serviceToken(version % 2 == 0 ? Realm.SERVICE_CLIENT : Realm.OTHER_SERVICE_CLIENT);
    awaitPublishedOnEveryBroker(tenantId, admin);
    return version + 1;
  }

  /** Waits until the tenant's partition leader accepts the token's command on every broker. */
  private static void awaitPublishedOnEveryBroker(final String tenantId, final String token) {
    Awaitility.await("every broker publishes a message for '%s'".formatted(tenantId))
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                CLUSTER
                    .brokers()
                    .values()
                    .forEach(
                        broker -> assertThat(publish(broker, tenantId, token)).isEqualTo(200)));
  }

  private static void awaitSecurityOutcomeOnEveryBroker(
      final String tenantId, final String outcome) {
    Awaitility.await("every broker reports %s for '%s'".formatted(outcome, tenantId))
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () ->
                CLUSTER
                    .brokers()
                    .values()
                    .forEach(
                        broker ->
                            assertThat(localSecurity(broker, tenantId).path("outcome").asText())
                                .isEqualTo(outcome)));
  }

  private static Map<MemberId, Object> moduleContexts(final String tenantId) {
    final Map<MemberId, Object> contexts = new LinkedHashMap<>();
    CLUSTER
        .brokers()
        .forEach(
            (id, broker) ->
                contexts.put(
                    id,
                    broker
                        .bean(io.camunda.zeebe.broker.Broker.class)
                        .getBrokerContext()
                        .getPhysicalTenantModules()
                        .context(tenantId)
                        .orElseThrow()));
    return contexts;
  }

  private static void create(final String tenantId, final Map<String, String> configuration) {
    assertThat(send("POST", broker(0), tenantId, configuration))
        .extracting(HttpResponse::statusCode)
        .isEqualTo(202);
    awaitRolledOut(tenantId, 1);
  }

  private static void awaitRolledOut(final String tenantId, final long version) {
    awaitRolledOut(broker(0), tenantId, version);
  }

  private static void awaitRolledOut(
      final TestStandaloneBroker broker, final String tenantId, final long version) {
    Awaitility.await("version %d of %s is applied by every broker".formatted(version, tenantId))
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var status = status(broker, tenantId);
              assertThat(status.get("version").asLong()).isEqualTo(version);
              assertThat(status.get("rolledOut").asBoolean()).isTrue();
              assertThat(status.get("changing").asBoolean()).isFalse();
            });
  }

  /** Logs the user in on a tenant through the authorization code flow, as a browser would. */
  private static Session login(
      final TestStandaloneBroker broker, final String tenantId, final Realm realm)
      throws Exception {
    return loginAt(broker, "/physical-tenants/" + tenantId, realm);
  }

  /** Logs the user in under a path prefix, {@code ""} for the unprefixed default tenant. */
  private static Session loginAt(
      final TestStandaloneBroker broker, final String prefix, final Realm realm) throws Exception {
    final var tenantId = prefix.isEmpty() ? "the unprefixed paths" : prefix;
    final var app = new Browser();
    final var idp = new Browser();
    final var base = broker.restAddress().toString().replaceAll("/$", "");
    final var start =
        app.send(
            HttpRequest.newBuilder(URI.create(base + prefix + "/oauth2/authorization/oidc"))
                .header("Accept", "text/html")
                .GET(),
            BodyHandlers.discarding());
    assertThat(start.statusCode()).as("login start on %s", tenantId).isEqualTo(302);
    final var loginPage =
        idp.send(
            HttpRequest.newBuilder(URI.create(location(start))).GET(), BodyHandlers.ofString());
    final var form =
        Pattern.compile("<form[^>]*kc-form-login[^>]*action=\"([^\"]+)\"")
            .matcher(loginPage.body());
    assertThat(form.find()).as("Keycloak login form of %s", realm.name()).isTrue();
    final var signIn =
        idp.send(
            HttpRequest.newBuilder(URI.create(form.group(1).replace("&amp;", "&")))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString("username=" + Realm.USER + "&password=" + PASSWORD)),
            BodyHandlers.ofString());
    assertThat(signIn.statusCode()).as("sign-in on %s", realm.name()).isEqualTo(302);
    final var callback =
        app.send(
            HttpRequest.newBuilder(URI.create(location(signIn))).GET(), BodyHandlers.ofString());
    assertThat(callback.statusCode())
        .as("login callback on %s: %s", tenantId, callback.body())
        .isEqualTo(302);
    return new Session(app, base + prefix);
  }

  private static String location(final HttpResponse<?> response) {
    return response.headers().firstValue("location").orElseThrow();
  }

  private static void assertOnEveryBroker(
      final String tenantId, final String token, final int status) {
    CLUSTER
        .brokers()
        .forEach(
            (id, broker) ->
                assertThat(rest(broker, tenantId, token))
                    .as("GET topology of '%s' on broker %s", tenantId, id)
                    .isEqualTo(status));
  }

  private static void awaitOnEveryBroker(
      final String tenantId, final String token, final int status) {
    Awaitility.await("'%s' answers %d on every broker".formatted(tenantId, status))
        .atMost(TIMEOUT)
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(() -> assertOnEveryBroker(tenantId, token, status));
  }

  private static Map<String, String> tenantConfiguration(
      final String tenantId, final Realm realm, final String clientIdClaim) {
    final Map<String, String> configuration = new LinkedHashMap<>();
    configuration.put("name", "Tenant " + tenantId);
    configuration.put("data.secondary-storage.type", "none");
    configuration.putAll(tenantSecurity(tenantId, realm, clientIdClaim));
    if (broker(0)
        .unifiedConfig()
        .getData()
        .getExporters()
        .containsKey(TestStandaloneBroker.RECORDING_EXPORTER_ID)) {
      configuration.put("data.exporters-assigned[0]", TestStandaloneBroker.RECORDING_EXPORTER_ID);
    }
    return configuration;
  }

  private static Map<String, String> tenantSecurity(
      final String tenantId, final Realm realm, final String clientIdClaim) {
    final Map<String, String> security = new LinkedHashMap<>();
    final var oidc = "security.authentication.oidc.";
    security.put(oidc + "issuer-uri", realm.issuer());
    security.put(oidc + "client-id", realm.webClient());
    security.put(oidc + "client-secret", "camunda.secrets." + tenantId + "-web");
    security.put(oidc + "audiences[0]", realm.audience());
    security.put(oidc + "username-claim", "absent_username");
    security.put(oidc + "client-id-claim", clientIdClaim);
    security.put("security.authentication.providers.assigned[0]", "oidc");
    security.put("security.authorizations.enabled", "true");
    security.put("security.initialization.default-roles.admin.clients[0]", Realm.SERVICE_CLIENT);
    security.put("security.initialization.default-roles.admin.clients[1]", Realm.ADMIN_BY_CLAIM);
    return security;
  }

  private static JsonNode localSecurity(final TestStandaloneBroker broker, final String tenantId) {
    return status(broker, tenantId).path("local").path("httpSecurity");
  }

  private static JsonNode status(final TestStandaloneBroker broker, final String tenantId) {
    final var response = send("GET", broker, tenantId, null);
    assertThat(response.statusCode()).isEqualTo(200);
    try {
      return JSON.readTree(response.body());
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static int rest(
      final TestStandaloneBroker broker, final String tenantId, final String token) {
    return request(broker, "GET", "/physical-tenants/" + tenantId + "/v2/topology", token, null)
        .statusCode();
  }

  private static int publish(
      final TestStandaloneBroker broker, final String tenantId, final String token) {
    return request(
            broker,
            "POST",
            "/physical-tenants/" + tenantId + "/v2/messages/publication",
            token,
            "{\"name\":\"probe\",\"correlationKey\":\"" + UUID.randomUUID() + "\"}")
        .statusCode();
  }

  private static HttpResponse<String> request(
      final TestStandaloneBroker broker,
      final String method,
      final String path,
      final String token,
      final String body) {
    final var request =
        HttpRequest.newBuilder(
                URI.create(broker.restAddress().toString().replaceAll("/$", "") + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    try {
      return HTTP.send(request.build(), BodyHandlers.ofString());
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static HttpResponse<String> send(
      final String method,
      final TestStandaloneBroker broker,
      final String tenantId,
      final Map<String, String> body) {
    try {
      final var request =
          HttpRequest.newBuilder(broker.actuatorUri("physicalTenants", tenantId))
              .header("Content-Type", "application/json")
              .method(
                  method,
                  body == null
                      ? BodyPublishers.noBody()
                      : BodyPublishers.ofString(JSON.writeValueAsString(body)))
              .build();
      return HTTP.send(request, BodyHandlers.ofString());
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static TestStandaloneBroker broker(final int nodeId) {
    return CLUSTER.brokers().get(MemberId.from(String.valueOf(nodeId)));
  }

  private static String directIssuer(final String realm) {
    return KEYCLOAK.getAuthServerUrl().replaceAll("/$", "") + "/realms/" + realm;
  }

  private static String proxiedIssuer(final String realm) {
    return "http://"
        + TOXIPROXY.getHost()
        + ":"
        + TOXIPROXY.getMappedPort(8666)
        + "/realms/"
        + realm;
  }

  private static void writeSecret(final String name, final String value) {
    try {
      Files.writeString(internalSecrets.resolve(name), value);
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String uniqueTenantId() {
    return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }

  /**
   * What the HTTP security of every broker in this JVM holds. Meters of traffic register a series
   * per topic, peer or outcome when it first occurs, also for the other tenants' traffic or a 503
   * while a restarted partition elects its leader, so they are not compared. Pooled threads that
   * start on demand up to a fixed size, such as the REST API executor's, which starts a thread per
   * request until its core size, are not counted.
   */
  private record SecurityResources(
      long scopeGenerations,
      long oidcClientStacks,
      long filterChains,
      long bearerConverters,
      long claimsConverters,
      int threads,
      Map<String, Long> threadNames,
      Set<String> meters) {

    /** What the security library keeps per served generation of every scope. */
    List<Long> chains() {
      return List.of(scopeGenerations, oidcClientStacks, filterChains);
    }

    Set<String> added(final SecurityResources before) {
      final var added = new java.util.TreeSet<>(meters);
      added.removeAll(before.meters());
      return added;
    }

    @Override
    public String toString() {
      return "generations=%d, oidcClientStacks=%d, filterChains=%d, bearerConverters=%d,"
              .formatted(scopeGenerations, oidcClientStacks, filterChains, bearerConverters)
          + " claimsConverters=%d, threads=%d, meters=%d"
              .formatted(claimsConverters, threads, meters.size());
    }

    static SecurityResources measure() {
      final var histogram = liveInstancesAfterFullGc();
      return new SecurityResources(
          histogram.getOrDefault("io.camunda.security.spring.scope.ScopeGeneration", 0L),
          histogram.getOrDefault("io.camunda.security.spring.oidc.ScopedOidcClients", 0L),
          histogram.getOrDefault("org.springframework.security.web.DefaultSecurityFilterChain", 0L),
          histogram.getOrDefault(
              "io.camunda.security.spring.converter.OidcTokenAuthenticationConverter", 0L),
          histogram.getOrDefault("io.camunda.security.core.authz.LazyTokenClaimsConverter", 0L),
          (int)
              Thread.getAllStackTraces().keySet().stream()
                  .map(Thread::getName)
                  .filter(name -> POOLED_THREADS.stream().noneMatch(name::startsWith))
                  .count(),
          Thread.getAllStackTraces().keySet().stream()
              .collect(
                  Collectors.groupingBy(
                      thread -> thread.getName().replaceAll("[0-9]+", "#"),
                      java.util.TreeMap::new,
                      Collectors.counting())),
          CLUSTER.brokers().entrySet().stream()
              .flatMap(
                  broker ->
                      broker
                          .getValue()
                          .bean(io.micrometer.core.instrument.MeterRegistry.class)
                          .getMeters()
                          .stream()
                          .filter(
                              meter ->
                                  TRAFFIC_METERS.stream()
                                      .noneMatch(meter.getId().getName()::startsWith))
                          .map(meter -> broker.getKey() + " " + meter.getId()))
              .collect(Collectors.toSet()));
    }

    private static Map<String, Long> liveInstancesAfterFullGc() {
      try {
        final String table =
            (String)
                java.lang.management.ManagementFactory.getPlatformMBeanServer()
                    .invoke(
                        new javax.management.ObjectName(
                            "com.sun.management:type=DiagnosticCommand"),
                        "gcClassHistogram",
                        new Object[] {new String[0]},
                        new String[] {String[].class.getName()});
        final Map<String, Long> instances = new HashMap<>();
        for (final String line : table.split("\n")) {
          final var columns = line.trim().split("\\s+");
          if (columns.length >= 4 && columns[0].endsWith(":")) {
            instances.merge(columns[3], Long.parseLong(columns[1]), Long::sum);
          }
        }
        return instances;
      } catch (final Exception e) {
        throw new IllegalStateException("Failed to read the class histogram", e);
      }
    }
  }

  /** A browser that logged in on a tenant. */
  private record Session(Browser browser, String tenantBase) {

    int topology() throws Exception {
      return browser
          .send(
              HttpRequest.newBuilder(URI.create(tenantBase + "/v2/topology"))
                  .header("Accept", "application/json")
                  .GET(),
              BodyHandlers.discarding())
          .statusCode();
    }
  }

  /**
   * A Keycloak realm acting as one identity provider: a confidential web client for the
   * authorization code flow, a service client whose tokens are admins by client id, a second
   * service client whose {@value #ALT_CLAIM} claim names an admin, and a user.
   */
  record Realm(
      String name,
      String issuer,
      String webClient,
      String webSecret,
      Map<String, String> serviceSecrets) {

    static final String WEB_CLIENT = "web";
    static final String SERVICE_CLIENT = "svc";
    static final String OTHER_SERVICE_CLIENT = "svc-other";
    static final String ALT_CLAIM = "alt_client";
    static final String ADMIN_BY_CLAIM = "admin-by-claim";
    static final String USER = "user";

    static Realm create(final String name, final String issuer) {
      return create(name, issuer, WEB_CLIENT, 300);
    }

    static Realm create(
        final String name,
        final String issuer,
        final String webClient,
        final int accessTokenLifespan) {
      final var webSecret = "web-" + UUID.randomUUID();
      final var serviceSecrets =
          Map.of(
              SERVICE_CLIENT,
              "svc-" + UUID.randomUUID(),
              OTHER_SERVICE_CLIENT,
              "other-" + UUID.randomUUID());
      final var realm = new Realm(name, issuer, webClient, webSecret, serviceSecrets);
      final var representation = new RealmRepresentation();
      representation.setRealm(name);
      representation.setEnabled(true);
      representation.setAccessTokenLifespan(accessTokenLifespan);
      representation.setClients(
          List.of(
              realm.client(webClient, webSecret, true, "nobody"),
              realm.client(SERVICE_CLIENT, serviceSecrets.get(SERVICE_CLIENT), false, "nobody"),
              realm.client(
                  OTHER_SERVICE_CLIENT,
                  serviceSecrets.get(OTHER_SERVICE_CLIENT),
                  false,
                  ADMIN_BY_CLAIM)));
      final var password = new CredentialRepresentation();
      password.setType(CredentialRepresentation.PASSWORD);
      password.setValue(PASSWORD);
      password.setTemporary(false);
      final var user = new UserRepresentation();
      user.setUsername(USER);
      user.setEnabled(true);
      user.setEmail(USER + "@" + name + ".example");
      user.setEmailVerified(true);
      user.setFirstName("Test");
      user.setLastName("User");
      user.setCredentials(List.of(password));
      representation.setUsers(List.of(user));
      try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
        admin.realms().create(representation);
      }
      return realm;
    }

    String audience() {
      return name + "-api";
    }

    /**
     * Gives the web client a new secret while the previous one stays valid, through Keycloak's
     * client secret rotation policy.
     *
     * @return the new secret
     */
    String rotateWebSecret() {
      try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
        final var realm = admin.realm(name);
        final var executor = new ClientPolicyExecutorRepresentation();
        executor.setExecutorProviderId("secret-rotation");
        executor.setConfiguration(
            RawJsonValue.of(
                Map.of(
                    "expiration-period", 3600,
                    "rotated-expiration-period", 600,
                    "remaining-rotation-period", 60)));
        final var profile = new ClientProfileRepresentation();
        profile.setName("rotation");
        profile.setExecutors(List.of(executor));
        final var profiles = new ClientProfilesRepresentation();
        profiles.setProfiles(List.of(profile));
        realm.clientPoliciesProfilesResource().updateProfiles(profiles);
        final var condition = new ClientPolicyConditionRepresentation();
        condition.setConditionProviderId("client-access-type");
        condition.setConfiguration(RawJsonValue.of(Map.of("type", List.of("confidential"))));
        final var policy = new ClientPolicyRepresentation();
        policy.setName("rotation");
        policy.setEnabled(true);
        policy.setConditions(List.of(condition));
        policy.setProfiles(List.of("rotation"));
        final var policies = new ClientPoliciesRepresentation();
        policies.setPolicies(List.of(policy));
        realm.clientPoliciesPoliciesResource().updatePolicies(policies);
        return webClientResource(realm).generateNewSecret().getValue();
      }
    }

    void revokeRotatedWebSecret() {
      try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
        webClientResource(admin.realm(name)).invalidateRotatedSecret();
      }
    }

    /** Whether Keycloak authenticates the web client with the given secret. */
    boolean acceptsWebSecret(final String secret) {
      final var form =
          "token=none&client_id="
              + URLEncoder.encode(webClient, StandardCharsets.UTF_8)
              + "&client_secret="
              + URLEncoder.encode(secret, StandardCharsets.UTF_8);
      try {
        return HTTP.send(
                    HttpRequest.newBuilder(
                            URI.create(issuer + "/protocol/openid-connect/token/introspect"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(BodyPublishers.ofString(form))
                        .build(),
                    BodyHandlers.discarding())
                .statusCode()
            == 200;
      } catch (final IOException e) {
        throw new IllegalStateException(e);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }

    /** Makes the web client authenticate with a JWT signed by any of the given keys. */
    void useSignedJwtForWebClient(final List<Keystore> keys) {
      try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
        final var realm = admin.realm(name);
        final var resource = webClientResource(realm);
        final var client = resource.toRepresentation();
        client.setClientAuthenticatorType("client-jwt");
        final Map<String, String> attributes = new HashMap<>(client.getAttributes());
        attributes.put("use.jwks.url", "false");
        attributes.put("use.jwks.string", "true");
        attributes.put(
            "jwks.string",
            JSON.writeValueAsString(Map.of("keys", keys.stream().map(Keystore::jwk).toList())));
        attributes.put("token.endpoint.auth.signing.alg", "RS256");
        client.setAttributes(attributes);
        resource.update(client);
        realm.clearKeysCache();
      } catch (final IOException e) {
        throw new IllegalStateException(e);
      }
    }

    private org.keycloak.admin.client.resource.ClientResource webClientResource(
        final org.keycloak.admin.client.resource.RealmResource realm) {
      return realm.clients().get(realm.clients().findByClientId(webClient).getFirst().getId());
    }

    String serviceToken(final String clientId) {
      return token(clientId, serviceSecrets.get(clientId));
    }

    String token(final String clientId, final String secret) {
      final var form =
          "grant_type=client_credentials&client_id="
              + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
              + "&client_secret="
              + URLEncoder.encode(secret, StandardCharsets.UTF_8);
      try {
        final var response =
            HTTP.send(
                HttpRequest.newBuilder(URI.create(issuer + "/protocol/openid-connect/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(BodyPublishers.ofString(form))
                    .build(),
                BodyHandlers.ofString());
        if (response.statusCode() != 200) {
          throw new IllegalStateException(
              "Token request to %s failed: %d %s"
                  .formatted(issuer, response.statusCode(), response.body()));
        }
        return JSON.readTree(response.body()).get("access_token").asText();
      } catch (final IOException e) {
        throw new IllegalStateException(e);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }

    private ClientRepresentation client(
        final String clientId, final String secret, final boolean web, final String altClient) {
      final var client = new ClientRepresentation();
      client.setClientId(clientId);
      client.setEnabled(true);
      client.setClientAuthenticatorType("client-secret");
      client.setSecret(secret);
      client.setStandardFlowEnabled(web);
      client.setServiceAccountsEnabled(!web);
      client.setDirectAccessGrantsEnabled(false);
      client.setRedirectUris(List.of("*"));
      client.setProtocolMappers(List.of(audienceMapper(), hardcodedClaim(ALT_CLAIM, altClient)));
      return client;
    }

    private ProtocolMapperRepresentation audienceMapper() {
      final var mapper = new ProtocolMapperRepresentation();
      mapper.setName("audience");
      mapper.setProtocol("openid-connect");
      mapper.setProtocolMapper("oidc-audience-mapper");
      mapper.setConfig(
          Map.of(
              "included.custom.audience", audience(),
              "access.token.claim", "true",
              "id.token.claim", "true"));
      return mapper;
    }

    private static ProtocolMapperRepresentation hardcodedClaim(
        final String claimName, final String claimValue) {
      final var mapper = new ProtocolMapperRepresentation();
      mapper.setName(claimName);
      mapper.setProtocol("openid-connect");
      mapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
      mapper.setConfig(
          Map.of(
              "claim.name", claimName,
              "claim.value", claimValue,
              "jsonType.label", "String",
              "access.token.claim", "true",
              "id.token.claim", "true"));
      return mapper;
    }
  }

  /** A PKCS12 keystore with one RSA key pair, made by the JDK's keytool. */
  record Keystore(Path path, String password, RSAPublicKey publicKey) {

    static final String ALIAS = "assertion";

    static Keystore generate(final Path path, final String password) throws Exception {
      final var process =
          new ProcessBuilder(
                  Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                  "-genkeypair",
                  "-alias",
                  ALIAS,
                  "-keyalg",
                  "RSA",
                  "-keysize",
                  "2048",
                  "-validity",
                  "2",
                  "-dname",
                  "CN=" + path.getFileName(),
                  "-storetype",
                  "PKCS12",
                  "-keystore",
                  path.toString(),
                  "-storepass",
                  password,
                  "-keypass",
                  password)
              .redirectErrorStream(true)
              .start();
      final var output = new String(process.getInputStream().readAllBytes());
      assertThat(process.waitFor()).as("keytool: %s", output).isZero();
      final var keyStore = KeyStore.getInstance("PKCS12");
      try (final var in = Files.newInputStream(path)) {
        keyStore.load(in, password.toCharArray());
      }
      return new Keystore(
          path, password, (RSAPublicKey) keyStore.getCertificate(ALIAS).getPublicKey());
    }

    /** The public key as a JWK, with the key id the security library derives by default. */
    Map<String, String> jwk() {
      try {
        final var encoder = Base64.getUrlEncoder().withoutPadding();
        return Map.of(
            "kty",
            "RSA",
            "use",
            "sig",
            "alg",
            "RS256",
            "kid",
            encoder.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded())),
            "n",
            encoder.encodeToString(unsigned(publicKey.getModulus())),
            "e",
            encoder.encodeToString(unsigned(publicKey.getPublicExponent())));
      } catch (final NoSuchAlgorithmException e) {
        throw new IllegalStateException(e);
      }
    }

    private static byte[] unsigned(final BigInteger value) {
      final var bytes = value.toByteArray();
      return bytes.length > 1 && bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }
  }

  /** Continuous REST requests to one tenant on every broker, with its own bearer token. */
  private static final class RestLoad {

    private final String tenantId;
    private final java.util.function.Supplier<String> tokens;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong succeeded = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
    private final List<Thread> threads = new ArrayList<>();
    private volatile String token;
    private volatile long tokenAt;

    private RestLoad(final String tenantId, final java.util.function.Supplier<String> tokens) {
      this.tenantId = tenantId;
      this.tokens = tokens;
    }

    void start() {
      CLUSTER
          .brokers()
          .forEach((id, broker) -> threads.add(Thread.ofVirtual().start(() -> run(id, broker))));
    }

    void stop() throws InterruptedException {
      running.set(false);
      for (final var thread : threads) {
        thread.join();
      }
    }

    long succeeded() {
      return succeeded.get();
    }

    long failed() {
      return failed.get();
    }

    List<String> failures() {
      return List.copyOf(failures);
    }

    private void run(final MemberId id, final TestStandaloneBroker broker) {
      while (running.get()) {
        try {
          final var status = rest(broker, tenantId, currentToken());
          if (status == 200) {
            succeeded.incrementAndGet();
          } else {
            failed.incrementAndGet();
            failures.add("broker " + id + ": " + status);
          }
        } catch (final RuntimeException e) {
          failed.incrementAndGet();
          failures.add("broker " + id + ": " + e);
        }
      }
    }

    private synchronized String currentToken() {
      if (token == null || System.nanoTime() - tokenAt > Duration.ofSeconds(60).toNanos()) {
        token = tokens.get();
        tokenAt = System.nanoTime();
      }
      return token;
    }
  }

  /**
   * A browser stand-in that replays the cookies a server set, by name only. The JDK cookie manager
   * does not send Keycloak's login cookies back over plain HTTP.
   */
  private static final class Browser {
    private final HttpClient client =
        HttpClient.newBuilder().followRedirects(Redirect.NEVER).build();
    private final Map<String, String> cookies = new LinkedHashMap<>();

    <T> HttpResponse<T> send(final HttpRequest.Builder request, final BodyHandler<T> handler)
        throws IOException, InterruptedException {
      if (!cookies.isEmpty()) {
        request.header(
            "Cookie",
            cookies.entrySet().stream()
                .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                .collect(Collectors.joining("; ")));
      }
      final var response = client.send(request.build(), handler);
      response.headers().allValues("set-cookie").forEach(this::store);
      return response;
    }

    private void store(final String setCookie) {
      final var attributes = setCookie.split(";");
      final var nameAndValue = attributes[0].split("=", 2);
      final var name = nameAndValue[0].trim();
      final var expired =
          nameAndValue.length < 2
              || nameAndValue[1].isBlank()
              || Arrays.stream(attributes)
                  .skip(1)
                  .anyMatch(attribute -> attribute.trim().equalsIgnoreCase("Max-Age=0"));
      if (expired) {
        cookies.remove(name);
      } else {
        cookies.put(name, nameAndValue[1].trim());
      }
    }
  }
}
