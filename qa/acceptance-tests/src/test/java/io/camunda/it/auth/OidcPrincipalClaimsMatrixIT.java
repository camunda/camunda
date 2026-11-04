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
import io.camunda.security.api.model.config.CsrfConfiguration;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
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
 *   <li><b>Path:</b> a REST bearer token, a gRPC bearer token, and a browser login. Each path is
 *       observed through the broker claims, the principal that the gateway attaches to the command
 *       it sends to the broker. The browser login sends its command with the session of the login.
 *   <li><b>Slot:</b> the root default slot ({@code oidc.*}), a root named provider ({@code
 *       providers.oidc.<id>}), a tenant default slot ({@code physical-tenants.<id>...oidc.*}), and
 *       a tenant named provider.
 *   <li><b>Claim:</b> {@code username-claim}, {@code client-id-claim}, {@code
 *       prefer-username-claim}.
 *   <li><b>State:</b> the slot sets the claim, or leaves it unset. Each slot has a case that sets
 *       every claim, one that sets none, and one that sets one claim and inherits the others, so a
 *       claim is resolved on its own and not as part of a block.
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

  private static final String DEFAULT_TENANT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String TENANT_C = "tenantc";
  private static final String LOGIN_USER = "matrix-user";
  private static final String LOGIN_PASSWORD = "matrix-password";

  // one realm, and so one issuer, per slot
  private static final String REALM_ROOT_DEFAULT = "matrix-root-default";
  private static final String REALM_ROOT_NAMED_SET = "matrix-root-named-set";
  private static final String REALM_ROOT_NAMED_UNSET = "matrix-root-named-unset";
  private static final String REALM_ROOT_NAMED_PARTIAL = "matrix-root-named-partial";
  private static final String REALM_TENANT_NAMED_SET = "matrix-tenant-named-set";
  private static final String REALM_TENANT_NAMED_UNSET = "matrix-tenant-named-unset";
  private static final String REALM_TENANT_NAMED_PARTIAL = "matrix-tenant-named-partial";

  // the claim settings of each slot; every set value differs from the value the slot inherits
  private static final ClaimSettings ROOT_DEFAULT_SET =
      new ClaimSettings("root_user", "root_client", true);
  private static final ClaimSettings ROOT_NAMED_SET =
      new ClaimSettings("root_named_user", "root_named_client", false);
  private static final ClaimSettings TENANT_DEFAULT_SET =
      new ClaimSettings("tenant_user", "tenant_client", false);
  private static final ClaimSettings TENANT_NAMED_SET =
      new ClaimSettings("tenant_named_user", "tenant_named_client", true);

  // one claim set, the others inherited
  private static final ClaimSettings ROOT_DEFAULT_PARTIAL =
      new ClaimSettings(null, "root_client", null);
  private static final ClaimSettings ROOT_NAMED_PARTIAL =
      new ClaimSettings("root_named_user", null, null);
  private static final ClaimSettings TENANT_DEFAULT_PARTIAL = new ClaimSettings(null, null, false);
  private static final ClaimSettings TENANT_NAMED_PARTIAL =
      new ClaimSettings(null, "tenant_named_client", null);

  private static final List<ClaimSettings> SET_SLOTS =
      List.of(ROOT_DEFAULT_SET, ROOT_NAMED_SET, TENANT_DEFAULT_SET, TENANT_NAMED_SET);

  /**
   * Paths and claims that the matrix does not cover yet, with the reason. {@link
   * #shouldCoverEveryCombinationOrListItHere()} checks that every slot, claim and state has a case,
   * that every slot has a partial case, and that every entry here gives a reason.
   */
  private static final Map<String, String> NOT_COVERED =
      Map.of(
          "path=BROWSER_LOGIN, claim=USERNAME_CLAIM or CLIENT_ID_CLAIM",
          "A provider has one login client, so its login token carries both claim sets. The"
              + " PREFER_USERNAME_CLAIM row shows the username or the client ID, whichever the"
              + " preference selects.",
          "path=MEMBERSHIPS",
          "Follow-up of #68212: memberships resolve groups, which belong with groups-claim.",
          "claim=GROUPS_CLAIM",
          "Follow-up of #68212: groups per provider land with #66967 and CSL ADR-0040.");

  // the root default slot sets every claim
  private static final PhysicalTenantsITHelper WITH_ROOT_CLAIMS_TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(DEFAULT_TENANT, Storage.rdbmsH2("matrix-with-default"))
          .withTenant(TENANT_A, Storage.rdbmsH2("matrix-with-tenanta"))
          .withTenant(TENANT_B, Storage.rdbmsH2("matrix-with-tenantb"))
          .withTenant(TENANT_C, Storage.rdbmsH2("matrix-with-tenantc"))
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER_WITH_ROOT_CLAIMS =
      WITH_ROOT_CLAIMS_TENANTS.configure(oidcBroker());

  // the root default slot sets no claim, so the library defaults apply
  private static final PhysicalTenantsITHelper WITHOUT_ROOT_CLAIMS_TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(DEFAULT_TENANT, Storage.rdbmsH2("matrix-without-default"))
          .withTenant(TENANT_A, Storage.rdbmsH2("matrix-without-tenanta"))
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER_WITHOUT_ROOT_CLAIMS =
      WITHOUT_ROOT_CLAIMS_TENANTS.configure(oidcBroker());

  // the root default slot sets only the client ID claim
  private static final PhysicalTenantsITHelper PARTIAL_ROOT_CLAIMS_TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(DEFAULT_TENANT, Storage.rdbmsH2("matrix-partial-default"))
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER_PARTIAL_ROOT_CLAIMS =
      PARTIAL_ROOT_CLAIMS_TENANTS.configure(oidcBroker());

  @BeforeAll
  static void startBrokers() {
    Stream.of(
            REALM_ROOT_DEFAULT,
            REALM_ROOT_NAMED_SET,
            REALM_ROOT_NAMED_UNSET,
            REALM_ROOT_NAMED_PARTIAL,
            REALM_TENANT_NAMED_SET,
            REALM_TENANT_NAMED_UNSET,
            REALM_TENANT_NAMED_PARTIAL)
        .forEach(OidcPrincipalClaimsMatrixIT::createRealm);

    configureWithRootClaims();
    configureWithoutRootClaims();
    rootDefaultSlot(BROKER_PARTIAL_ROOT_CLAIMS, ROOT_DEFAULT_PARTIAL);
    BROKER_WITH_ROOT_CLAIMS.start();
    BROKER_WITHOUT_ROOT_CLAIMS.start();
    BROKER_PARTIAL_ROOT_CLAIMS.start();
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

    // when the token is sent, or the browser logs in and sends a command with its session
    final Principal actual =
        path == Path.BROWSER_LOGIN
            ? sessionPrincipal(matrixCase)
            : bearerPrincipal(matrixCase, path, token(matrixCase.realm(), claim.tokenClient()));

    // then
    assertThat(actual).as("%s", name).isEqualTo(expected);
  }

  @Test
  void shouldCoverEveryCombinationOrListItHere() {
    final Set<String> covered = new HashSet<>();
    final Set<Slot> partial = new HashSet<>();
    cases()
        .forEach(
            matrixCase -> {
              Arrays.stream(Claim.values())
                  .forEach(
                      claim ->
                          covered.add(
                              matrixCase.slot() + "/" + claim + "/" + claim.state(matrixCase)));
              if (matrixCase.own().isPartial()) {
                partial.add(matrixCase.slot());
              }
            });
    final List<String> missing = new ArrayList<>();
    for (final Slot slot : Slot.values()) {
      for (final Claim claim : Claim.values()) {
        for (final State state : State.values()) {
          if (!covered.contains(slot + "/" + claim + "/" + state)) {
            missing.add(slot + "/" + claim + "/" + state);
          }
        }
      }
    }
    assertThat(missing).as("slot/claim/state combinations without a case").isEmpty();
    assertThat(partial).as("slots with a partial case").containsExactlyInAnyOrder(Slot.values());
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
                                            "%s, %s %s, over %s"
                                                .formatted(
                                                    matrixCase,
                                                    claim,
                                                    claim.state(matrixCase),
                                                    path),
                                            matrixCase,
                                            claim,
                                            path,
                                            expectedPrincipal(matrixCase, claim, path)))));
  }

  /**
   * One case per slot and claim settings. Each case names the cluster, the physical tenant whose
   * path the token is sent to, the realm that issues the token, the registration that a browser
   * logs in with, the claims that the slot sets, and the claim settings that the written rule
   * resolves for it.
   */
  private static Stream<MatrixCase> cases() {
    final ClaimSettings none = ClaimSettings.NONE;
    final ClaimSettings rootDefault = ROOT_DEFAULT_SET;
    final ClaimSettings tenantADefault = TENANT_DEFAULT_SET.orElse(rootDefault);
    final var with = Cluster.WITH_ROOT_CLAIMS;
    final var without = Cluster.WITHOUT_ROOT_CLAIMS;
    final var partial = Cluster.PARTIAL_ROOT_CLAIMS;
    return Stream.of(
        // the root default slot
        new MatrixCase(
            with, Slot.ROOT_DEFAULT, DEFAULT_TENANT, REALM_ROOT_DEFAULT, "oidc", rootDefault, none),
        new MatrixCase(
            without, Slot.ROOT_DEFAULT, DEFAULT_TENANT, REALM_ROOT_DEFAULT, "oidc", none, none),
        new MatrixCase(
            partial,
            Slot.ROOT_DEFAULT,
            DEFAULT_TENANT,
            REALM_ROOT_DEFAULT,
            "oidc",
            ROOT_DEFAULT_PARTIAL,
            none),
        // a root named provider inherits from the root default slot
        new MatrixCase(
            with,
            Slot.ROOT_NAMED,
            DEFAULT_TENANT,
            REALM_ROOT_NAMED_SET,
            "rootnamedset",
            ROOT_NAMED_SET,
            rootDefault),
        new MatrixCase(
            with,
            Slot.ROOT_NAMED,
            DEFAULT_TENANT,
            REALM_ROOT_NAMED_UNSET,
            "rootnamedunset",
            none,
            rootDefault),
        new MatrixCase(
            with,
            Slot.ROOT_NAMED,
            DEFAULT_TENANT,
            REALM_ROOT_NAMED_PARTIAL,
            "rootnamedpartial",
            ROOT_NAMED_PARTIAL,
            rootDefault),
        new MatrixCase(
            without,
            Slot.ROOT_NAMED,
            DEFAULT_TENANT,
            REALM_ROOT_NAMED_UNSET,
            "rootnamedunset",
            none,
            none),
        // a tenant default slot inherits from the root default slot
        new MatrixCase(
            with,
            Slot.TENANT_DEFAULT,
            TENANT_A,
            REALM_ROOT_DEFAULT,
            "oidc",
            TENANT_DEFAULT_SET,
            rootDefault),
        new MatrixCase(
            with, Slot.TENANT_DEFAULT, TENANT_B, REALM_ROOT_DEFAULT, "oidc", none, rootDefault),
        new MatrixCase(
            with,
            Slot.TENANT_DEFAULT,
            TENANT_C,
            REALM_ROOT_DEFAULT,
            "oidc",
            TENANT_DEFAULT_PARTIAL,
            rootDefault),
        new MatrixCase(
            without, Slot.TENANT_DEFAULT, TENANT_A, REALM_ROOT_DEFAULT, "oidc", none, none),
        // a tenant named provider inherits from the tenant default slot
        new MatrixCase(
            with,
            Slot.TENANT_NAMED,
            TENANT_A,
            REALM_TENANT_NAMED_SET,
            "tenantnamedset",
            TENANT_NAMED_SET,
            tenantADefault),
        new MatrixCase(
            with,
            Slot.TENANT_NAMED,
            TENANT_A,
            REALM_TENANT_NAMED_UNSET,
            "tenantnamedunset",
            none,
            tenantADefault),
        new MatrixCase(
            with,
            Slot.TENANT_NAMED,
            TENANT_A,
            REALM_TENANT_NAMED_PARTIAL,
            "tenantnamedpartial",
            TENANT_NAMED_PARTIAL,
            tenantADefault),
        new MatrixCase(
            without,
            Slot.TENANT_NAMED,
            TENANT_A,
            REALM_TENANT_NAMED_UNSET,
            "tenantnamedunset",
            none,
            none));
  }

  /**
   * The principal of the written rule: a user when the username claim is preferred and present, or
   * when there is no client ID; else a client. A bearer token's claims are read from a token of the
   * realm. A browser login token carries every candidate claim of the realm, and its {@code sub} is
   * the ID of the Keycloak user.
   */
  private static Principal expectedPrincipal(
      final MatrixCase matrixCase, final Claim claim, final Path path) {
    final Map<String, Object> tokenClaims =
        path == Path.BROWSER_LOGIN
            ? loginTokenClaims(matrixCase.realm())
            : claims(token(matrixCase.realm(), claim.tokenClient()));
    final ClaimSettings resolved = matrixCase.resolved().withDefaults();
    final Object username = tokenClaims.get(resolved.usernameClaim());
    final Object clientId =
        resolved.clientIdClaim() == null ? null : tokenClaims.get(resolved.clientIdClaim());
    if ((resolved.preferUsernameClaim() && username != null) || clientId == null) {
      return Principal.user(String.valueOf(username));
    }
    return Principal.client(String.valueOf(clientId));
  }

  private static Map<String, Object> loginTokenClaims(final String realm) {
    final Map<String, Object> claims = new HashMap<>();
    SET_SLOTS.forEach(
        settings -> {
          claims.put(settings.usernameClaim(), claimValue(realm, settings.usernameClaim()));
          claims.put(settings.clientIdClaim(), claimValue(realm, settings.clientIdClaim()));
        });
    try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
      claims.put("sub", admin.realm(realm).users().search(LOGIN_USER).getFirst().getId());
    }
    return claims;
  }

  /** Sends a deployment with the token and reads the principal the gateway gave the broker. */
  private static Principal bearerPrincipal(
      final MatrixCase matrixCase, final Path path, final String token) {
    final String processId = nextProcessId();
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
    return brokerPrincipal(processId);
  }

  /**
   * Logs in with the case's registration on the case's tenant, sends a deployment with the session
   * of the login, and reads the principal the gateway gave the broker.
   */
  private static Principal sessionPrincipal(final MatrixCase matrixCase) {
    final URI restAddress = matrixCase.cluster().broker().restAddress();
    final String pathPrefix =
        DEFAULT_TENANT.equals(matrixCase.tenant())
            ? ""
            : "/physical-tenants/" + matrixCase.tenant();
    final String processId = nextProcessId();
    Awaitility.await("a deployment with the session of %s succeeds".formatted(matrixCase))
        .atMost(Duration.ofSeconds(120))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var response =
                  OidcBrowserLogin.loginAndSend(
                      restAddress,
                      pathPrefix,
                      matrixCase.registrationId(),
                      LOGIN_USER,
                      LOGIN_PASSWORD,
                      deployment(pathPrefix, processId));
              assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            });
    return brokerPrincipal(processId);
  }

  private static Function<URI, HttpRequest.Builder> deployment(
      final String pathPrefix, final String processId) {
    final String boundary = "matrix-" + processId;
    final String body =
        "--%s\r\nContent-Disposition: form-data; name=\"resources\"; filename=\"%s.bpmn\"\r\n"
                .formatted(boundary, processId)
            + "Content-Type: application/xml\r\n\r\n"
            + Bpmn.convertToString(
                Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
            + "\r\n--%s--\r\n".formatted(boundary);
    return restAddress ->
        HttpRequest.newBuilder(restAddress.resolve(pathPrefix + "/v2/deployments"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
  }

  private static Principal brokerPrincipal(final String processId) {
    final Map<String, Object> claims =
        RecordingExporter.deploymentRecords(DeploymentIntent.CREATE)
            .onlyCommands()
            .withResourceName(processId + ".bpmn")
            .getFirst()
            .getAuthorizations();
    final Object username = claims.get(Authorization.AUTHORIZED_USERNAME);
    final Object clientId = claims.get(Authorization.AUTHORIZED_CLIENT_ID);
    return username != null
        ? Principal.user(String.valueOf(username))
        : Principal.client(String.valueOf(clientId));
  }

  private static String nextProcessId() {
    return "matrix_" + PROCESS_IDS.incrementAndGet();
  }

  private static TestStandaloneBroker oidcBroker() {
    // the broker claims are read from the exported deployment commands; the helper assigns the
    // exporter to every tenant only when it is already registered. CSRF protection is off, because
    // the browser login sends its deployment with the session, and CSRF is not under test here.
    return new TestStandaloneBroker()
        .withRecordingExporter(true)
        .withAuthenticatedAccess()
        .withAuthenticationMethod(AuthenticationMethod.OIDC)
        .withSecurityConfig(
            c -> {
              c.getAuthorizations().setEnabled(false);
              final var csrf = new CsrfConfiguration();
              csrf.setEnabled(false);
              c.setCsrf(csrf);
            });
  }

  private static void configureWithRootClaims() {
    final var broker = BROKER_WITH_ROOT_CLAIMS;
    rootDefaultSlot(broker, ROOT_DEFAULT_SET);
    rootProvider(broker, "rootnamedset", REALM_ROOT_NAMED_SET, ROOT_NAMED_SET);
    rootProvider(broker, "rootnamedunset", REALM_ROOT_NAMED_UNSET, ClaimSettings.NONE);
    rootProvider(broker, "rootnamedpartial", REALM_ROOT_NAMED_PARTIAL, ROOT_NAMED_PARTIAL);
    // the default tenant assigns nothing, so it keeps every root provider

    // tenanta overrides its default slot and has three providers of its own
    tenantClaims(broker, TENANT_A, TENANT_DEFAULT_SET);
    tenantProvider(broker, TENANT_A, "tenantnamedset", REALM_TENANT_NAMED_SET, TENANT_NAMED_SET);
    tenantProvider(
        broker, TENANT_A, "tenantnamedunset", REALM_TENANT_NAMED_UNSET, ClaimSettings.NONE);
    tenantProvider(
        broker, TENANT_A, "tenantnamedpartial", REALM_TENANT_NAMED_PARTIAL, TENANT_NAMED_PARTIAL);
    assign(broker, TENANT_A, "oidc", "tenantnamedset", "tenantnamedunset", "tenantnamedpartial");

    // tenantb keeps the root default slot, and tenantc overrides one claim of it
    assign(broker, TENANT_B, "oidc");
    tenantClaims(broker, TENANT_C, TENANT_DEFAULT_PARTIAL);
    assign(broker, TENANT_C, "oidc");
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
      final TestStandaloneBroker broker, final String tenantId, final ClaimSettings settings) {
    claimProperties(tenantAuth(tenantId) + "oidc.", settings).forEach(broker::withProperty);
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
    clients.add(loginClient(realmName, candidateClaims(realmName, true, true)));
    realm.setClients(clients);
    realm.setUsers(List.of(loginUser()));
    try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
      admin.realms().create(realm);
    }
  }

  private static ClientRepresentation tokenClient(final String realmName, final Claim claim) {
    final var client = new ClientRepresentation();
    client.setClientId(claim.tokenClient());
    client.setEnabled(true);
    client.setClientAuthenticatorType("client-secret");
    client.setSecret(claim.tokenClient());
    client.setServiceAccountsEnabled(true);
    client.setProtocolMappers(
        candidateClaims(realmName, claim.carriesUsernameClaims(), claim.carriesClientIdClaims()));
    return client;
  }

  /** The client that a browser logs in with. */
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

  private static List<ProtocolMapperRepresentation> candidateClaims(
      final String realmName, final boolean usernameClaims, final boolean clientIdClaims) {
    final Set<String> claimNames = new HashSet<>();
    if (usernameClaims) {
      SET_SLOTS.forEach(settings -> claimNames.add(settings.usernameClaim()));
    }
    if (clientIdClaims) {
      SET_SLOTS.forEach(settings -> claimNames.add(settings.clientIdClaim()));
    }
    return claimNames.stream()
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

    /**
     * A provider has one login client, so a browser login token carries both claim sets. It shows
     * the claim that the preference selects.
     */
    boolean covers(final Claim claim) {
      return this != BROWSER_LOGIN || claim == Claim.PREFER_USERNAME_CLAIM;
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
   * The claim under test, and the bearer token that shows it. A token with only username claims
   * shows the username claim, one with only client ID claims shows the client ID claim, and one
   * with both shows which of the two is preferred.
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

    /** Whether the slot of the case sets this claim. */
    State state(final MatrixCase matrixCase) {
      final ClaimSettings own = matrixCase.own();
      final Object value =
          switch (this) {
            case USERNAME_CLAIM -> own.usernameClaim();
            case CLIENT_ID_CLAIM -> own.clientIdClaim();
            case PREFER_USERNAME_CLAIM -> own.preferUsernameClaim();
          };
      return value != null ? State.SET : State.UNSET;
    }
  }

  enum Cluster {
    WITH_ROOT_CLAIMS,
    WITHOUT_ROOT_CLAIMS,
    PARTIAL_ROOT_CLAIMS;

    TestStandaloneBroker broker() {
      return switch (this) {
        case WITH_ROOT_CLAIMS -> BROKER_WITH_ROOT_CLAIMS;
        case WITHOUT_ROOT_CLAIMS -> BROKER_WITHOUT_ROOT_CLAIMS;
        case PARTIAL_ROOT_CLAIMS -> BROKER_PARTIAL_ROOT_CLAIMS;
      };
    }

    PhysicalTenantsITHelper tenants() {
      return switch (this) {
        case WITH_ROOT_CLAIMS -> WITH_ROOT_CLAIMS_TENANTS;
        case WITHOUT_ROOT_CLAIMS -> WITHOUT_ROOT_CLAIMS_TENANTS;
        case PARTIAL_ROOT_CLAIMS -> PARTIAL_ROOT_CLAIMS_TENANTS;
      };
    }
  }

  /**
   * @param own the claims that the slot itself sets
   * @param parent the resolved claims of the slot it inherits from, {@link ClaimSettings#NONE} for
   *     the root default slot
   */
  record MatrixCase(
      Cluster cluster,
      Slot slot,
      String tenant,
      String realm,
      String registrationId,
      ClaimSettings own,
      ClaimSettings parent) {

    /** The claim settings of the written rule: each claim the slot leaves unset is inherited. */
    ClaimSettings resolved() {
      return own.orElse(parent);
    }

    @Override
    public String toString() {
      return "%s %s on %s (%s)".formatted(cluster, slot, tenant, registrationId);
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

    /** Whether the slot sets some claims, but not all. */
    boolean isPartial() {
      final long set =
          Stream.of(usernameClaim, clientIdClaim, preferUsernameClaim)
              .filter(value -> value != null)
              .count();
      return set > 0 && set < 3;
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
