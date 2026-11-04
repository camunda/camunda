/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.CredentialsProvider;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Checks that every authentication path resolves the same principal for a token, for each place
 * that can configure a principal claim (camunda/camunda#68212).
 *
 * <p>The matrix is path × config slot × claim × set/unset:
 *
 * <ul>
 *   <li><b>Path:</b> a REST bearer token, a gRPC bearer token, and a browser login. The two bearer
 *       paths are observed through the broker claims, the principal that the gateway attaches to
 *       the command it sends to the broker. The browser login is observed through the username of
 *       its session.
 *   <li><b>Slot:</b> the root default slot ({@code oidc.*}), a root named provider ({@code
 *       providers.oidc.<id>}), a tenant default slot ({@code physical-tenants.<id>...oidc.*}), and
 *       a tenant named provider.
 *   <li><b>Claim:</b> {@code username-claim}, {@code client-id-claim}, {@code
 *       prefer-username-claim}.
 *   <li><b>State:</b> the slot sets the claim, or leaves it unset.
 * </ul>
 *
 * <p>The expected principal comes from the written rule, not from the code under test: a tenant
 * default slot keeps each root value it does not set (ADR-0007 D4), a named provider uses the
 * claims of the default slot of its scope for each claim it does not set (CSL ADR-0034), and an
 * unset claim falls back to the library default.
 *
 * <p>Every value that a slot sets differs from the value it would inherit, and every claim name
 * that a slot sets differs from the library default. {@code prefer-username-claim} is a boolean, so
 * a slot that inherits {@code true} can only set {@code false}, the default; there, the case checks
 * that the slot does not inherit. Every token carries each candidate claim with a value that names
 * its realm and its claim, so the resolved principal shows which slot was used. Each slot has its
 * own Keycloak realm, that is its own issuer.
 */
@Testcontainers
@ZeebeIntegration
final class OidcPrincipalClaimsMatrixIT {

  @Container
  static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final AtomicInteger PROCESS_IDS = new AtomicInteger();

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String LOGIN_USER = "matrix-user";
  private static final String LOGIN_PASSWORD = "matrix-password";

  // one realm, and so one issuer, per slot
  private static final String REALM_ROOT_DEFAULT = "matrix-root-default";
  private static final String REALM_ROOT_NAMED_SET = "matrix-root-named-set";
  private static final String REALM_ROOT_NAMED_UNSET = "matrix-root-named-unset";
  private static final String REALM_TENANT_NAMED_SET = "matrix-tenant-named-set";
  private static final String REALM_TENANT_NAMED_UNSET = "matrix-tenant-named-unset";

  // the claim settings of each slot; every set value differs from the value the slot inherits
  private static final ClaimSettings ROOT_DEFAULT_SET =
      new ClaimSettings("root_user", "root_client", true);
  private static final ClaimSettings ROOT_NAMED_SET =
      new ClaimSettings("root_named_user", "root_named_client", false);
  private static final ClaimSettings TENANT_DEFAULT_SET =
      new ClaimSettings("tenant_user", "tenant_client", false);
  private static final ClaimSettings TENANT_NAMED_SET =
      new ClaimSettings("tenant_named_user", "tenant_named_client", true);

  private static final List<ClaimSettings> SET_SLOTS =
      List.of(ROOT_DEFAULT_SET, ROOT_NAMED_SET, TENANT_DEFAULT_SET, TENANT_NAMED_SET);

  /**
   * Paths and claims that the matrix does not cover yet, with the reason. {@link
   * #shouldCoverEveryCombinationOrListItHere()} checks that every slot and state has a case, and
   * that every entry here gives a reason.
   */
  private static final Map<String, String> NOT_COVERED =
      Map.of(
          "path=BROWSER_LOGIN, claim=CLIENT_ID_CLAIM or PREFER_USERNAME_CLAIM",
          "A browser login is always a user: its token carries no client ID claim, and the session"
              + " shows only the username.",
          "path=MEMBERSHIPS",
          "Follow-up of #68212: memberships resolve groups, which belong with groups-claim.",
          "claim=GROUPS_CLAIM",
          "Follow-up of #68212: groups per provider land with #66967 and CSL ADR-0040.");

  // the root default slot sets every claim
  // the session username of a browser login is read from /me, which needs secondary storage
  private static final PhysicalTenantsITHelper WITH_ROOT_CLAIMS_TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(
              PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.rdbmsH2("matrix-with-default"))
          .withTenant(TENANT_A, Storage.rdbmsH2("matrix-with-tenanta"))
          .withTenant(TENANT_B, Storage.rdbmsH2("matrix-with-tenantb"))
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER_WITH_ROOT_CLAIMS =
      WITH_ROOT_CLAIMS_TENANTS.configure(oidcBroker());

  // the root default slot sets no claim, so the library defaults apply
  private static final PhysicalTenantsITHelper WITHOUT_ROOT_CLAIMS_TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(
              PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.rdbmsH2("matrix-without-default"))
          .withTenant(TENANT_A, Storage.rdbmsH2("matrix-without-tenanta"))
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER_WITHOUT_ROOT_CLAIMS =
      WITHOUT_ROOT_CLAIMS_TENANTS.configure(oidcBroker());

  @BeforeAll
  static void startBrokers() {
    Stream.of(
            REALM_ROOT_DEFAULT,
            REALM_ROOT_NAMED_SET,
            REALM_ROOT_NAMED_UNSET,
            REALM_TENANT_NAMED_SET,
            REALM_TENANT_NAMED_UNSET)
        .forEach(OidcPrincipalClaimsMatrixIT::createRealm);

    configureWithRootClaims();
    configureWithoutRootClaims();
    BROKER_WITH_ROOT_CLAIMS.start();
    BROKER_WITHOUT_ROOT_CLAIMS.start();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("matrix")
  void shouldResolveThePrincipalOfTheWrittenRule(
      final String name,
      final MatrixCase matrixCase,
      final Claim claim,
      final Path path,
      final Principal expected) {
    // given the case's realm, whose tokens carry every candidate claim with a value of its own

    // when the token is sent, or the browser logs in
    final Principal actual =
        path == Path.BROWSER_LOGIN
            ? Principal.user(sessionUsername(matrixCase))
            : brokerPrincipal(matrixCase, path, token(matrixCase.realm(), claim.tokenClient()));

    // then
    assertThat(actual)
        .as(
            "path %s, slot %s, claim %s, %s (%s)",
            path, matrixCase.slot(), claim, matrixCase.state(), matrixCase.cluster())
        .isEqualTo(expected);
  }

  @Test
  void shouldCoverEveryCombinationOrListItHere() {
    final Set<String> covered = new HashSet<>();
    cases().forEach(matrixCase -> covered.add(matrixCase.slot() + "/" + matrixCase.state().name()));
    final List<String> missing = new ArrayList<>();
    for (final Slot slot : Slot.values()) {
      for (final State state : State.values()) {
        if (!covered.contains(slot + "/" + state.name())) {
          missing.add(slot + "/" + state);
        }
      }
    }
    assertThat(missing).as("slot/state combinations without a case").isEmpty();
    assertThat(NOT_COVERED)
        .as("every left-out combination gives a reason")
        .allSatisfy((combination, reason) -> assertThat(reason).isNotBlank());
  }

  static Stream<Arguments> matrix() {
    return cases()
        .flatMap(
            matrixCase ->
                Arrays.stream(Claim.values())
                    .flatMap(
                        claim ->
                            Arrays.stream(Path.values())
                                .filter(path -> path.covers(claim))
                                .map(
                                    path ->
                                        Arguments.of(
                                            "%s %s %s, %s over %s"
                                                .formatted(
                                                    matrixCase.cluster(),
                                                    matrixCase.slot(),
                                                    matrixCase.state(),
                                                    claim,
                                                    path),
                                            matrixCase,
                                            claim,
                                            path,
                                            path == Path.BROWSER_LOGIN
                                                ? expectedSessionPrincipal(matrixCase)
                                                : expectedPrincipal(matrixCase, claim)))));
  }

  /**
   * One case per slot and state. Each case names the cluster, the physical tenant whose path the
   * token is sent to, the realm that issues the token, the registration that a browser logs in
   * with, and the claim settings that the written rule resolves for that slot.
   */
  private static Stream<MatrixCase> cases() {
    final ClaimSettings rootDefault = ROOT_DEFAULT_SET;
    final ClaimSettings tenantADefault = TENANT_DEFAULT_SET.orElse(rootDefault);
    final ClaimSettings none = ClaimSettings.NONE;
    return Stream.of(
        // with root claims
        new MatrixCase(
            Cluster.WITH_ROOT_CLAIMS,
            Slot.ROOT_DEFAULT,
            State.SET,
            PhysicalTenantsITHelper.DEFAULT_TENANT_ID,
            REALM_ROOT_DEFAULT,
            "oidc",
            rootDefault),
        new MatrixCase(
            Cluster.WITH_ROOT_CLAIMS,
            Slot.ROOT_NAMED,
            State.SET,
            PhysicalTenantsITHelper.DEFAULT_TENANT_ID,
            REALM_ROOT_NAMED_SET,
            "rootnamedset",
            ROOT_NAMED_SET.orElse(rootDefault)),
        new MatrixCase(
            Cluster.WITH_ROOT_CLAIMS,
            Slot.ROOT_NAMED,
            State.UNSET,
            PhysicalTenantsITHelper.DEFAULT_TENANT_ID,
            REALM_ROOT_NAMED_UNSET,
            "rootnamedunset",
            none.orElse(rootDefault)),
        new MatrixCase(
            Cluster.WITH_ROOT_CLAIMS,
            Slot.TENANT_DEFAULT,
            State.SET,
            TENANT_A,
            REALM_ROOT_DEFAULT,
            "oidc",
            tenantADefault),
        new MatrixCase(
            Cluster.WITH_ROOT_CLAIMS,
            Slot.TENANT_DEFAULT,
            State.UNSET,
            TENANT_B,
            REALM_ROOT_DEFAULT,
            "oidc",
            none.orElse(rootDefault)),
        new MatrixCase(
            Cluster.WITH_ROOT_CLAIMS,
            Slot.TENANT_NAMED,
            State.SET,
            TENANT_A,
            REALM_TENANT_NAMED_SET,
            "tenantnamedset",
            TENANT_NAMED_SET.orElse(tenantADefault)),
        new MatrixCase(
            Cluster.WITH_ROOT_CLAIMS,
            Slot.TENANT_NAMED,
            State.UNSET,
            TENANT_A,
            REALM_TENANT_NAMED_UNSET,
            "tenantnamedunset",
            none.orElse(tenantADefault)),
        // without root claims: each unset slot ends at the library defaults
        new MatrixCase(
            Cluster.WITHOUT_ROOT_CLAIMS,
            Slot.ROOT_DEFAULT,
            State.UNSET,
            PhysicalTenantsITHelper.DEFAULT_TENANT_ID,
            REALM_ROOT_DEFAULT,
            "oidc",
            none),
        new MatrixCase(
            Cluster.WITHOUT_ROOT_CLAIMS,
            Slot.ROOT_NAMED,
            State.UNSET,
            PhysicalTenantsITHelper.DEFAULT_TENANT_ID,
            REALM_ROOT_NAMED_UNSET,
            "rootnamedunset",
            none),
        new MatrixCase(
            Cluster.WITHOUT_ROOT_CLAIMS,
            Slot.TENANT_DEFAULT,
            State.UNSET,
            TENANT_A,
            REALM_ROOT_DEFAULT,
            "oidc",
            none),
        new MatrixCase(
            Cluster.WITHOUT_ROOT_CLAIMS,
            Slot.TENANT_NAMED,
            State.UNSET,
            TENANT_A,
            REALM_TENANT_NAMED_UNSET,
            "tenantnamedunset",
            none));
  }

  /**
   * The principal of the written rule: a user when the username claim is preferred and present, or
   * when there is no client ID; else a client. The expectation reads the claims of a token from the
   * realm, so it needs no running cluster.
   */
  private static Principal expectedPrincipal(final MatrixCase matrixCase, final Claim claim) {
    final Map<String, Object> tokenClaims = claims(token(matrixCase.realm(), claim.tokenClient()));
    final ClaimSettings resolved = matrixCase.resolved().withDefaults();
    final Object username = tokenClaims.get(resolved.usernameClaim());
    final Object clientId =
        resolved.clientIdClaim() == null ? null : tokenClaims.get(resolved.clientIdClaim());
    if ((resolved.preferUsernameClaim() && username != null) || clientId == null) {
      return Principal.user(String.valueOf(username));
    }
    return Principal.client(String.valueOf(clientId));
  }

  /**
   * The username of a browser login: the value of the resolved username claim in the login token.
   * When the claim ends at the default {@code sub}, it is the ID of the Keycloak user.
   */
  private static Principal expectedSessionPrincipal(final MatrixCase matrixCase) {
    final String usernameClaim = matrixCase.resolved().withDefaults().usernameClaim();
    if (!"sub".equals(usernameClaim)) {
      return Principal.user(claimValue(matrixCase.realm(), usernameClaim));
    }
    try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
      return Principal.user(
          admin.realm(matrixCase.realm()).users().search(LOGIN_USER).getFirst().getId());
    }
  }

  /** Logs in with the case's registration on the case's tenant and reads the session username. */
  private static String sessionUsername(final MatrixCase matrixCase) {
    final URI restAddress = matrixCase.cluster().broker().restAddress();
    final String pathPrefix =
        PhysicalTenantsITHelper.DEFAULT_TENANT_ID.equals(matrixCase.tenant())
            ? ""
            : "/physical-tenants/" + matrixCase.tenant();
    Awaitility.await("the login of %s is served".formatted(matrixCase.registrationId()))
        .atMost(Duration.ofSeconds(120))
        .ignoreExceptions()
        .until(
            () ->
                OidcBrowserLogin.isLoginServed(
                    restAddress, pathPrefix, matrixCase.registrationId()));
    try {
      return OidcBrowserLogin.loginAndReadUsername(
          restAddress, pathPrefix, matrixCase.registrationId(), LOGIN_USER, LOGIN_PASSWORD);
    } catch (final Exception e) {
      throw new IllegalStateException("Browser login of " + matrixCase + " failed", e);
    }
  }

  /** Sends a deployment with the token and reads the principal the gateway gave the broker. */
  private static Principal brokerPrincipal(
      final MatrixCase matrixCase, final Path path, final String token) {
    final String processId = "matrix_" + PROCESS_IDS.incrementAndGet();
    final String resourceName = processId + ".bpmn";
    try (final CamundaClient client =
        matrixCase
            .cluster()
            .tenants()
            .newClientBuilder(matrixCase.cluster().broker(), matrixCase.tenant())
            .preferRestOverGrpc(path == Path.REST)
            .credentialsProvider(bearer(token))
            .build()) {
      matrixCase
          .cluster()
          .tenants()
          .deploy(
              client,
              Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
              processId);
    }
    final Map<String, Object> claims =
        RecordingExporter.deploymentRecords(DeploymentIntent.CREATE)
            .onlyCommands()
            .withResourceName(resourceName)
            .getFirst()
            .getAuthorizations();
    final Object username = claims.get(Authorization.AUTHORIZED_USERNAME);
    final Object clientId = claims.get(Authorization.AUTHORIZED_CLIENT_ID);
    return username != null
        ? Principal.user(String.valueOf(username))
        : Principal.client(String.valueOf(clientId));
  }

  private static TestStandaloneBroker oidcBroker() {
    // the broker claims are read from the exported deployment commands; the helper assigns the
    // exporter to every tenant only when it is already registered
    return new TestStandaloneBroker()
        .withRecordingExporter(true)
        .withAuthenticatedAccess()
        .withAuthenticationMethod(AuthenticationMethod.OIDC)
        .withSecurityConfig(c -> c.getAuthorizations().setEnabled(false));
  }

  private static void configureWithRootClaims() {
    final var broker = BROKER_WITH_ROOT_CLAIMS;
    rootDefaultSlot(broker, ROOT_DEFAULT_SET);
    rootProvider(broker, "rootnamedset", REALM_ROOT_NAMED_SET, ROOT_NAMED_SET);
    rootProvider(broker, "rootnamedunset", REALM_ROOT_NAMED_UNSET, ClaimSettings.NONE);
    // the default tenant assigns nothing, so it keeps every root provider

    // tenanta overrides its default slot and has two providers of its own
    tenantClaims(broker, TENANT_A, "oidc", TENANT_DEFAULT_SET);
    tenantProvider(broker, TENANT_A, "tenantnamedset", REALM_TENANT_NAMED_SET, TENANT_NAMED_SET);
    tenantProvider(
        broker, TENANT_A, "tenantnamedunset", REALM_TENANT_NAMED_UNSET, ClaimSettings.NONE);
    assign(broker, TENANT_A, "oidc", "tenantnamedset", "tenantnamedunset");

    // tenantb keeps the root default slot
    assign(broker, TENANT_B, "oidc");
  }

  private static void configureWithoutRootClaims() {
    final var broker = BROKER_WITHOUT_ROOT_CLAIMS;
    rootDefaultSlot(broker, ClaimSettings.NONE);
    rootProvider(broker, "rootnamedunset", REALM_ROOT_NAMED_UNSET, ClaimSettings.NONE);
    tenantProvider(
        broker, TENANT_A, "tenantnamedunset", REALM_TENANT_NAMED_UNSET, ClaimSettings.NONE);
    assign(broker, TENANT_A, "oidc", "tenantnamedunset");
  }

  private static void rootDefaultSlot(
      final TestStandaloneBroker broker, final ClaimSettings settings) {
    broker.withSecurityConfig(
        c -> {
          final var oidc = c.getAuthentication().getOidc();
          oidc.setIssuerUri(issuerUri(REALM_ROOT_DEFAULT));
          oidc.setClientId(loginClient(REALM_ROOT_DEFAULT));
          oidc.setClientSecret(loginSecret(REALM_ROOT_DEFAULT));
          oidc.setRedirectUri("{baseUrl}/sso-callback");
        });
    claimProperties("camunda.security.authentication.oidc.", settings)
        .forEach(broker::withProperty);
  }

  private static void rootProvider(
      final TestStandaloneBroker broker,
      final String id,
      final String realm,
      final ClaimSettings settings) {
    provider(broker, "camunda.security.authentication.providers.oidc." + id + ".", realm, settings);
  }

  private static void tenantProvider(
      final TestStandaloneBroker broker,
      final String tenantId,
      final String id,
      final String realm,
      final ClaimSettings settings) {
    provider(broker, tenantAuth(tenantId) + "providers.oidc." + id + ".", realm, settings);
  }

  private static void provider(
      final TestStandaloneBroker broker,
      final String prefix,
      final String realm,
      final ClaimSettings settings) {
    broker
        .withProperty(prefix + "issuer-uri", issuerUri(realm))
        .withProperty(prefix + "client-id", loginClient(realm))
        .withProperty(prefix + "client-secret", loginSecret(realm))
        .withProperty(prefix + "redirect-uri", "{baseUrl}/sso-callback");
    claimProperties(prefix, settings).forEach(broker::withProperty);
  }

  private static void tenantClaims(
      final TestStandaloneBroker broker,
      final String tenantId,
      final String slot,
      final ClaimSettings settings) {
    claimProperties(tenantAuth(tenantId) + slot + ".", settings).forEach(broker::withProperty);
  }

  private static void assign(
      final TestStandaloneBroker broker, final String tenantId, final String... providerIds) {
    for (int i = 0; i < providerIds.length; i++) {
      broker.withProperty(tenantAuth(tenantId) + "providers.assigned[" + i + "]", providerIds[i]);
    }
  }

  private static Map<String, String> claimProperties(
      final String prefix, final ClaimSettings settings) {
    final Map<String, String> properties = new LinkedHashMap<>();
    if (settings.usernameClaim() != null) {
      properties.put(prefix + "username-claim", settings.usernameClaim());
    }
    if (settings.clientIdClaim() != null) {
      properties.put(prefix + "client-id-claim", settings.clientIdClaim());
    }
    if (settings.preferUsernameClaim() != null) {
      properties.put(
          prefix + "prefer-username-claim", String.valueOf(settings.preferUsernameClaim()));
    }
    return properties;
  }

  private static String tenantAuth(final String tenantId) {
    return "camunda.physical-tenants." + tenantId + ".security.authentication.";
  }

  private static String issuerUri(final String realm) {
    return KEYCLOAK.getAuthServerUrl() + "/realms/" + realm;
  }

  private static CredentialsProvider bearer(final String token) {
    return new CredentialsProvider() {
      @Override
      public void applyCredentials(final CredentialsApplier applier) {
        applier.put("Authorization", "Bearer " + token);
      }

      @Override
      public boolean shouldRetryRequest(final StatusCode statusCode) {
        return false;
      }
    };
  }

  private static String token(final String realm, final String clientId) {
    final String form =
        "grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientId;
    final var request =
        HttpRequest.newBuilder()
            .uri(URI.create(issuerUri(realm) + "/protocol/openid-connect/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
    try {
      final var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).as("token of %s in %s", clientId, realm).isEqualTo(200);
      return JSON.readTree(response.body()).get("access_token").asText();
    } catch (final Exception e) {
      throw new IllegalStateException("Could not get a token from realm " + realm, e);
    }
  }

  private static Map<String, Object> claims(final String token) {
    final String payload = token.split("\\.")[1];
    try {
      return JSON.readValue(
          new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8),
          new TypeReference<>() {});
    } catch (final Exception e) {
      throw new IllegalStateException("Could not read the claims of a token", e);
    }
  }

  /**
   * A realm with one service client per token kind, and a login client and user for the browser
   * login. Every candidate claim carries a value that names the realm and the claim, so a resolved
   * value shows which claim was read.
   */
  private static void createRealm(final String realmName) {
    final var realm = new RealmRepresentation();
    realm.setRealm(realmName);
    realm.setEnabled(true);
    final List<ClientRepresentation> clients =
        new ArrayList<>(
            Arrays.stream(Claim.values()).map(claim -> tokenClient(realmName, claim)).toList());
    clients.add(loginClient(realmName, usernameClaims(realmName)));
    realm.setClients(clients);
    realm.setUsers(List.of(loginUser()));
    try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
      admin.realms().create(realm);
    }
  }

  private static ClientRepresentation tokenClient(final String realmName, final Claim claim) {
    final Set<String> claimNames = new HashSet<>();
    if (claim.carriesUsernameClaims()) {
      SET_SLOTS.forEach(settings -> claimNames.add(settings.usernameClaim()));
    }
    if (claim.carriesClientIdClaims()) {
      SET_SLOTS.forEach(settings -> claimNames.add(settings.clientIdClaim()));
    }
    final var client = new ClientRepresentation();
    client.setClientId(claim.tokenClient());
    client.setEnabled(true);
    client.setClientAuthenticatorType("client-secret");
    client.setSecret(claim.tokenClient());
    client.setServiceAccountsEnabled(true);
    client.setProtocolMappers(
        claimNames.stream()
            .map(name -> hardcodedClaim(name, claimValue(realmName, name)))
            .toList());
    return client;
  }

  /** The client that a browser logs in with; its tokens carry the username claims of every slot. */
  private static ClientRepresentation loginClient(
      final String realmName, final List<ProtocolMapperRepresentation> mappers) {
    final var client = new ClientRepresentation();
    client.setClientId(loginClient(realmName));
    client.setEnabled(true);
    client.setClientAuthenticatorType("client-secret");
    client.setSecret(loginSecret(realmName));
    client.setStandardFlowEnabled(true);
    client.setRedirectUris(List.of("*"));
    client.setProtocolMappers(mappers);
    return client;
  }

  private static List<ProtocolMapperRepresentation> usernameClaims(final String realmName) {
    return SET_SLOTS.stream()
        .map(ClaimSettings::usernameClaim)
        .map(name -> hardcodedClaim(name, claimValue(realmName, name)))
        .toList();
  }

  private static UserRepresentation loginUser() {
    final var password = new CredentialRepresentation();
    password.setType(CredentialRepresentation.PASSWORD);
    password.setValue(LOGIN_PASSWORD);
    password.setTemporary(false);
    final var user = new UserRepresentation();
    user.setUsername(LOGIN_USER);
    user.setEnabled(true);
    user.setEmail(LOGIN_USER + "@example.com");
    user.setEmailVerified(true);
    user.setFirstName("Matrix");
    user.setLastName("User");
    user.setCredentials(List.of(password));
    return user;
  }

  private static String loginClient(final String realm) {
    return realm + "-login";
  }

  private static String loginSecret(final String realm) {
    return realm + "-login-secret";
  }

  /** The value of a claim in a token of {@code realm}: it names the realm and the claim. */
  private static String claimValue(final String realm, final String claim) {
    return realm + "/" + claim;
  }

  private static ProtocolMapperRepresentation hardcodedClaim(
      final String name, final String value) {
    final var mapper = new ProtocolMapperRepresentation();
    mapper.setName(name + "-mapper");
    mapper.setProtocol("openid-connect");
    mapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
    mapper.setConfig(
        Map.of(
            "claim.name",
            name,
            "claim.value",
            value,
            "jsonType.label",
            "String",
            "access.token.claim",
            "true",
            "id.token.claim",
            "true",
            "userinfo.token.claim",
            "true"));
    return mapper;
  }

  enum Path {
    REST,
    GRPC,
    BROWSER_LOGIN;

    /** A browser login is always a user, so it shows only the username claim. */
    boolean covers(final Claim claim) {
      return this != BROWSER_LOGIN || claim == Claim.USERNAME_CLAIM;
    }
  }

  enum Slot {
    ROOT_DEFAULT,
    ROOT_NAMED,
    TENANT_DEFAULT,
    TENANT_NAMED
  }

  enum State {
    SET,
    UNSET
  }

  /**
   * The claim under test, and the token that shows it. A token with only username claims shows the
   * username claim, one with only client ID claims shows the client ID claim, and one with both
   * shows which of the two is preferred.
   */
  enum Claim {
    USERNAME_CLAIM("username-token", true, false),
    CLIENT_ID_CLAIM("client-id-token", false, true),
    PREFER_USERNAME_CLAIM("both-token", true, true);

    private final String tokenClient;
    private final boolean carriesUsernameClaims;
    private final boolean carriesClientIdClaims;

    Claim(
        final String tokenClient,
        final boolean carriesUsernameClaims,
        final boolean carriesClientIdClaims) {
      this.tokenClient = tokenClient;
      this.carriesUsernameClaims = carriesUsernameClaims;
      this.carriesClientIdClaims = carriesClientIdClaims;
    }

    String tokenClient() {
      return tokenClient;
    }

    boolean carriesUsernameClaims() {
      return carriesUsernameClaims;
    }

    boolean carriesClientIdClaims() {
      return carriesClientIdClaims;
    }
  }

  enum Cluster {
    WITH_ROOT_CLAIMS,
    WITHOUT_ROOT_CLAIMS;

    TestStandaloneBroker broker() {
      return this == WITH_ROOT_CLAIMS ? BROKER_WITH_ROOT_CLAIMS : BROKER_WITHOUT_ROOT_CLAIMS;
    }

    PhysicalTenantsITHelper tenants() {
      return this == WITH_ROOT_CLAIMS ? WITH_ROOT_CLAIMS_TENANTS : WITHOUT_ROOT_CLAIMS_TENANTS;
    }
  }

  record MatrixCase(
      Cluster cluster,
      Slot slot,
      State state,
      String tenant,
      String realm,
      String registrationId,
      ClaimSettings resolved) {

    @Override
    public String toString() {
      return cluster + " " + slot + " " + state;
    }
  }

  /** The claims that a slot sets; {@code null} means the slot leaves the claim unset. */
  record ClaimSettings(String usernameClaim, String clientIdClaim, Boolean preferUsernameClaim) {

    static final ClaimSettings NONE = new ClaimSettings(null, null, null);

    /** Each claim that this slot leaves unset comes from {@code parent}. */
    ClaimSettings orElse(final ClaimSettings parent) {
      return new ClaimSettings(
          usernameClaim != null ? usernameClaim : parent.usernameClaim,
          clientIdClaim != null ? clientIdClaim : parent.clientIdClaim,
          preferUsernameClaim != null ? preferUsernameClaim : parent.preferUsernameClaim);
    }

    /** The library defaults: {@code sub}, no client ID claim, and the client ID preferred. */
    ClaimSettings withDefaults() {
      return orElse(new ClaimSettings("sub", null, false));
    }
  }

  record Principal(String kind, String id) {
    static Principal user(final String username) {
      return new Principal("user", username);
    }

    static Principal client(final String clientId) {
      return new Principal("client", clientId);
    }
  }
}
