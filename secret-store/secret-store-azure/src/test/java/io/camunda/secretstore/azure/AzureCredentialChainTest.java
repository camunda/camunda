/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static io.camunda.secretstore.azure.StubAzureHttpClient.environment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.exception.ClientAuthenticationException;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpPipelineBuilder;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.policy.BearerTokenAuthenticationPolicy;
import com.azure.core.util.Configuration;
import com.azure.core.util.Context;
import io.camunda.secretstore.SecretStoreMetricsDoc;
import io.camunda.secretstore.azure.StubAzureHttpClient.Request;
import io.camunda.zeebe.test.util.logging.LogCapturer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.Level;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AzureCredentialChainTest {

  private static final String PHYSICAL_TENANT = "pt1";
  private static final String VAULT_URL = "https://my-vault.vault.azure.net";
  private static final String US_GOVERNMENT_AUTHORITY = "https://login.microsoftonline.us/";

  // MSAL caches managed-identity tokens in static state, keyed by identity and scope, so a token
  // another test (or test class) got for the same scope would answer without an IMDS request
  private final TokenRequestContext vaultScope =
      new TokenRequestContext()
          .addScopes("https://" + UUID.randomUUID() + ".vault.azure.net/.default");

  private final ExecutorService executor = Executors.newCachedThreadPool();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  @TempDir private Path tempDir;

  @AfterEach
  void shutDownExecutor() {
    executor.shutdownNow();
  }

  @Test
  void shouldTryOnlyTheProductionSignInMethodsInOrder() {
    // given
    final var config = AzureKeyVaultStoreConfig.of(VAULT_URL);

    // when
    final var credentials =
        AzureCredentialChain.credentials(
            config,
            environment(Map.of()),
            StubAzureHttpClient.offline(),
            executor,
            new SignInMonitor(PHYSICAL_TENANT, "default", VAULT_URL, new SimpleMeterRegistry()));

    // then — no developer login (Azure CLI, IDEs, PowerShell, Azure Developer CLI) is ever tried
    assertThat(credentials)
        .extracting(SignInCredential::name)
        .containsExactly(
            AzureCredentialChain.ENVIRONMENT,
            AzureCredentialChain.WORKLOAD_IDENTITY,
            AzureCredentialChain.MANAGED_IDENTITY);
  }

  @Test
  void shouldSignInAsTheServicePrincipalFromTheEnvironment() {
    // given
    final var http = StubAzureHttpClient.signingIn(3600);
    final var credential =
        chain(
            AzureKeyVaultStoreConfig.of(VAULT_URL),
            servicePrincipalEnvironment("sp-client-id", "sp-secret"),
            http);

    // when
    final var token = credential.getTokenSync(vaultScope);

    // then
    assertThat(token.getToken()).isEqualTo(StubAzureHttpClient.TOKEN);
    assertThat(http.tokenRequests())
        .singleElement()
        .satisfies(
            request -> {
              assertThat(request.url())
                  .startsWith("https://login.microsoftonline.com/tenant-id/oauth2/");
              assertThat(request.body()).contains("client_id=sp-client-id");
            });
  }

  @Test
  void shouldSignInAsTheServicePrincipalWithACertificateFromTheEnvironment() throws Exception {
    // given
    final var http = StubAzureHttpClient.signingIn(3600);
    final var environment =
        environment(
            Map.of(
                "AZURE_TENANT_ID", "tenant-id",
                "AZURE_CLIENT_ID", "sp-cert-client-id",
                "AZURE_CLIENT_CERTIFICATE_PATH", certificate("cert-password").toString(),
                "AZURE_CLIENT_CERTIFICATE_PASSWORD", "cert-password"));
    final var credential = chain(AzureKeyVaultStoreConfig.of(VAULT_URL), environment, http);

    // when
    final var token = credential.getTokenSync(vaultScope);

    // then — signed in with an assertion signed by the certificate, not a secret
    assertThat(token.getToken()).isEqualTo(StubAzureHttpClient.TOKEN);
    assertThat(http.tokenRequests())
        .singleElement()
        .satisfies(
            request ->
                assertThat(request.body())
                    .contains("client_id=sp-cert-client-id", "client_assertion=")
                    .doesNotContain("client_secret"));
  }

  @Test
  void shouldSignInWithWorkloadIdentityAsTheConfiguredIdentity() throws Exception {
    // given
    final var http = StubAzureHttpClient.signingIn(3600);
    final var credential =
        chain(
            config("tenant-identity", null),
            workloadIdentityEnvironment("pod-client-id", "federated-token"),
            http);

    // when
    final var token = credential.getTokenSync(vaultScope);

    // then
    assertThat(token.getToken()).isEqualTo(StubAzureHttpClient.TOKEN);
    assertThat(http.tokenRequests())
        .singleElement()
        .satisfies(
            request -> {
              assertThat(request.body())
                  .contains("client_id=tenant-identity")
                  .contains("client_assertion=federated-token");
            });
  }

  @Test
  void shouldSignInWithManagedIdentityAsTheConfiguredIdentity() {
    // given
    final var http = StubAzureHttpClient.signingIn(3600);
    final var credential = chain(config("vm-identity", null), environment(Map.of()), http);

    // when
    final var token = credential.getTokenSync(vaultScope);

    // then
    assertThat(token.getToken()).isEqualTo(StubAzureHttpClient.TOKEN);
    assertThat(http.tokenRequests())
        .singleElement()
        .satisfies(
            request -> {
              assertThat(request.isImdsRequest()).isTrue();
              assertThat(request.url()).contains("client_id=vm-identity");
            });
  }

  @Test
  void shouldSignInWithTheSystemAssignedIdentityWithoutAClientId() {
    // given
    final var http = StubAzureHttpClient.signingIn(3600);
    final var credential =
        chain(AzureKeyVaultStoreConfig.of(VAULT_URL), environment(Map.of()), http);

    // when
    credential.getTokenSync(vaultScope);

    // then
    assertThat(http.tokenRequests())
        .singleElement()
        .satisfies(
            request -> {
              assertThat(request.isImdsRequest()).isTrue();
              assertThat(request.url()).doesNotContain("client_id=");
            });
  }

  @Test
  void shouldSignInWithTheSystemAssignedIdentityDespiteAClientIdInTheEnvironment() {
    // given — AZURE_CLIENT_ID alone, as left over from a service principal or another tool
    final var http = StubAzureHttpClient.signingIn(3600);
    final var environment = environment(Map.of("AZURE_CLIENT_ID", "unrelated-client-id"));
    final var credential = chain(AzureKeyVaultStoreConfig.of(VAULT_URL), environment, http);

    // when
    credential.getTokenSync(vaultScope);

    // then
    assertThat(http.tokenRequests())
        .singleElement()
        .satisfies(
            request -> {
              assertThat(request.isImdsRequest()).isTrue();
              assertThat(request.url()).doesNotContain("client_id=");
            });
    assertThat(managedIdentityDescription(AzureKeyVaultStoreConfig.of(VAULT_URL), environment))
        .contains("system-assigned identity");
  }

  @Test
  void shouldDescribeTheManagedIdentityByTheClientIdItUsesInAWorkloadIdentityPod()
      throws Exception {
    // when
    final var description =
        managedIdentityDescription(
            AzureKeyVaultStoreConfig.of(VAULT_URL),
            workloadIdentityEnvironment("pod-client-id", "federated-token"));

    // then — the SDK exchanges the federated token for AZURE_CLIENT_ID, not the system identity
    assertThat(description).contains("pod-client-id").doesNotContain("system-assigned");
  }

  @Test
  void shouldSignEachPhysicalTenantInAsItsOwnIdentity() {
    // given — the same process environment, as two tenants of one broker share it
    final var tenantA = StubAzureHttpClient.signingIn(3600);
    final var tenantB = StubAzureHttpClient.signingIn(3600);
    final var credentialA = chain(config("identity-a", null), environment(Map.of()), tenantA);
    final var credentialB = chain(config("identity-b", null), environment(Map.of()), tenantB);

    // when
    credentialA.getTokenSync(vaultScope);
    credentialB.getTokenSync(vaultScope);

    // then
    assertThat(tenantA.tokenRequests())
        .extracting(Request::url)
        .singleElement()
        .asString()
        .contains("client_id=identity-a");
    assertThat(tenantB.tokenRequests())
        .extracting(Request::url)
        .singleElement()
        .asString()
        .contains("client_id=identity-b");
  }

  @Test
  void shouldSignInAsAServicePrincipalThroughTheConfiguredAuthority() {
    // given
    final var http = StubAzureHttpClient.signingIn(3600);
    final var credential =
        chain(
            config(null, US_GOVERNMENT_AUTHORITY),
            servicePrincipalEnvironment("sp-client-id", "sp-secret"),
            http);

    // when
    credential.getTokenSync(vaultScope);

    // then
    assertThat(http.requests())
        .isNotEmpty()
        .allSatisfy(request -> assertThat(request.host()).isEqualTo("login.microsoftonline.us"));
  }

  @Test
  void shouldSignInWithWorkloadIdentityThroughTheConfiguredAuthority() throws Exception {
    // given
    final var http = StubAzureHttpClient.signingIn(3600);
    final var credential =
        chain(
            config(null, US_GOVERNMENT_AUTHORITY),
            workloadIdentityEnvironment("pod-client-id", "federated-token"),
            http);

    // when
    credential.getTokenSync(vaultScope);

    // then
    assertThat(http.requests())
        .isNotEmpty()
        .allSatisfy(request -> assertThat(request.host()).isEqualTo("login.microsoftonline.us"));
  }

  @Test
  void shouldFailToSignInWhenNoProductionSignInMethodIsAvailable() {
    // given — no AZURE_* variables and no IMDS, as on a developer's machine, where a CLI or IDE
    // login would be the only identity left
    final var http = StubAzureHttpClient.offline();
    final var credential =
        chain(AzureKeyVaultStoreConfig.of(VAULT_URL), environment(Map.of()), http);

    // when/then
    assertThatThrownBy(() -> credential.getTokenSync(vaultScope))
        .isInstanceOf(ClientAuthenticationException.class);
    assertThat(http.requests())
        .isNotEmpty()
        .allSatisfy(request -> assertThat(request.isImdsRequest()).isTrue());
    // counted once by the chain, not once per unavailable method
    assertThat(signInFailures()).isEqualTo(1);
  }

  @Test
  void shouldNotSignInAsAUserFromAUsernameAndPasswordInTheEnvironment() {
    // given — a username and password, the SDK's fallback when no service principal is complete
    final var http = StubAzureHttpClient.signingIn(3600);
    final var environment =
        environment(
            Map.of(
                "AZURE_TENANT_ID", "tenant-id",
                "AZURE_CLIENT_ID", "app-client-id",
                "AZURE_USERNAME", "person@example.com",
                "AZURE_PASSWORD", "person-password"));
    final var credential = chain(config("fallback-identity", null), environment, http);

    // when
    credential.getTokenSync(vaultScope);

    // then — the chain moves past the environment to the managed identity
    assertThat(http.requests())
        .isNotEmpty()
        .allSatisfy(request -> assertThat(request.isImdsRequest()).isTrue());
  }

  @Test
  void shouldNotFallBackToAnotherIdentityWhenTheServicePrincipalIsRejected() {
    // given
    final var http = StubAzureHttpClient.rejecting();
    final var credential =
        chain(
            config("vm-identity", null),
            servicePrincipalEnvironment("sp-client-id", "wrong-secret"),
            http);

    // when/then
    assertThatThrownBy(() -> credential.getTokenSync(vaultScope))
        .isInstanceOf(ClientAuthenticationException.class);
    assertThat(http.requests()).noneMatch(Request::isImdsRequest);
    // counted once by the rejected credential, not again by the chain it ended
    assertThat(signInFailures()).isEqualTo(1);
  }

  @Test
  void shouldNotFallBackToManagedIdentityWhenTheWorkloadIdentityIsMalformed() throws Exception {
    // given — a workload-identity pod whose tenant ID is not a valid tenant ID
    final var tokenFile = Files.writeString(tempDir.resolve("azure-identity-token"), "token");
    final var environment =
        environment(
            Map.of(
                "AZURE_TENANT_ID", "not a tenant/id",
                "AZURE_CLIENT_ID", "pod-client-id",
                "AZURE_FEDERATED_TOKEN_FILE", tokenFile.toString()));
    final var http = StubAzureHttpClient.signingIn(3600);

    // when/then — the misconfiguration surfaces instead of signing in as the managed identity
    assertThatThrownBy(() -> chain(config("vm-identity", null), environment, http))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenant ID");
    assertThat(http.requests()).isEmpty();
  }

  @Test
  void shouldNotLetTheVaultClientLogTheTokenOfASignInResponseItCannotParse() {
    // given Entra ID answering with a token response cut off inside the token, which the SDK's
    // JSON parser quotes in its error, and the vault client's bearer-token policy, whose token
    // cache logs every failed sign-in at ERROR whatever the identity loggers are set to
    final var http =
        StubAzureHttpClient.answeringSignIn(
            200,
            "{\"token_type\":\"Bearer\",\"expires_in\":3600,\"access_token\":\""
                + StubAzureHttpClient.TOKEN);
    final var credential =
        chain(
            AzureKeyVaultStoreConfig.of(VAULT_URL),
            servicePrincipalEnvironment("sp-client-id", "sp-secret"),
            http);
    final var vault =
        new HttpPipelineBuilder()
            .httpClient(http)
            .policies(
                new BearerTokenAuthenticationPolicy(credential, "https://vault.azure.net/.default"))
            .build();

    try (final var log = LogCapturer.capturing("com.azure.core", Level.DEBUG)) {
      // when
      assertThatThrownBy(() -> vault.sendSync(secretRequest(), Context.NONE))
          .isInstanceOf(ClientAuthenticationException.class)
          .satisfies(
              failure ->
                  assertThat(causes(failure)).noneMatch(AzureCredentialChainTest::quotesTheToken));
      assertThatThrownBy(() -> vault.send(secretRequest()).block())
          .isInstanceOf(ClientAuthenticationException.class)
          .satisfies(
              failure ->
                  assertThat(causes(failure)).noneMatch(AzureCredentialChainTest::quotesTheToken));

      // then
      assertThat(log.messages()).isNotEmpty();
      assertThat(log.containsInMessagesOrThrowables(StubAzureHttpClient.TOKEN)).isFalse();
    }
  }

  @Test
  void shouldWarnWhenTheServicePrincipalInTheEnvironmentIsIncomplete() {
    // given — a secret without its tenant
    final var environment =
        environment(Map.of("AZURE_CLIENT_ID", "sp-client-id", "AZURE_CLIENT_SECRET", "sp-secret"));

    try (final var log = LogCapturer.capturing(AzureCredentialChain.class, Level.WARN)) {
      // when
      chain(AzureKeyVaultStoreConfig.of(VAULT_URL), environment, StubAzureHttpClient.offline());

      // then
      assertThat(log.messages())
          .singleElement()
          .asString()
          .contains("AZURE_TENANT_ID")
          .doesNotContain("AZURE_CLIENT_ID")
          .doesNotContain("sp-secret");
    }
  }

  @Test
  void shouldNotWarnAboutAServicePrincipalInAWorkloadIdentityPod() throws Exception {
    // given — AKS sets AZURE_CLIENT_ID for workload identity, not for a service principal
    final var environment = workloadIdentityEnvironment("pod-client-id", "federated-token");

    try (final var log = LogCapturer.capturing(AzureCredentialChain.class, Level.WARN)) {
      // when
      chain(AzureKeyVaultStoreConfig.of(VAULT_URL), environment, StubAzureHttpClient.offline());

      // then
      assertThat(log.messages()).isEmpty();
    }
  }

  @Test
  void shouldDescribeTheServicePrincipalByItsClientIdButNotItsSecret() {
    // when
    final var credentials =
        AzureCredentialChain.credentials(
            AzureKeyVaultStoreConfig.of(VAULT_URL),
            servicePrincipalEnvironment("sp-client-id", "sp-secret"),
            StubAzureHttpClient.offline(),
            executor,
            new SignInMonitor(PHYSICAL_TENANT, "default", VAULT_URL, meterRegistry));

    // then — the description is what the sign-in log line names
    assertThat(credentials.getFirst().description())
        .contains(AzureCredentialChain.ENVIRONMENT)
        .contains("sp-client-id")
        .doesNotContain("sp-secret");
  }

  private String managedIdentityDescription(
      final AzureKeyVaultStoreConfig config, final Configuration environment) {
    return AzureCredentialChain.credentials(
            config,
            environment,
            StubAzureHttpClient.offline(),
            executor,
            new SignInMonitor(PHYSICAL_TENANT, "default", VAULT_URL, meterRegistry))
        .getLast()
        .description();
  }

  private TokenCredential chain(
      final AzureKeyVaultStoreConfig config,
      final Configuration environment,
      final StubAzureHttpClient http) {
    return AzureCredentialChain.build(
        config,
        environment,
        http,
        executor,
        new SignInMonitor(PHYSICAL_TENANT, "default", config.vaultUrl(), meterRegistry));
  }

  private double signInFailures() {
    return meterRegistry.get(SecretStoreMetricsDoc.SIGNIN_FAILURES.getName()).counter().count();
  }

  private static boolean quotesTheToken(final Throwable failure) {
    return failure.getMessage() != null && failure.getMessage().contains(StubAzureHttpClient.TOKEN);
  }

  private static List<Throwable> causes(final Throwable failure) {
    final var causes = new ArrayList<Throwable>();
    for (Throwable t = failure; t != null && !causes.contains(t); t = t.getCause()) {
      causes.add(t);
    }
    return causes;
  }

  private static HttpRequest secretRequest() {
    return new HttpRequest(HttpMethod.GET, VAULT_URL + "/secrets/db-password?api-version=7.6");
  }

  private static AzureKeyVaultStoreConfig config(
      final @Nullable String managedIdentityClientId, final @Nullable String authorityHost) {
    return new AzureKeyVaultStoreConfig(
        VAULT_URL,
        null,
        null,
        managedIdentityClientId,
        authorityHost,
        AzureKeyVaultStoreConfig.DEFAULT_MAX_RETRIES,
        AzureKeyVaultStoreConfig.DEFAULT_CALL_TIMEOUT,
        AzureKeyVaultStoreConfig.DEFAULT_ATTEMPT_TIMEOUT);
  }

  private static Configuration servicePrincipalEnvironment(
      final String clientId, final String clientSecret) {
    return environment(
        Map.of(
            "AZURE_TENANT_ID", "tenant-id",
            "AZURE_CLIENT_ID", clientId,
            "AZURE_CLIENT_SECRET", clientSecret));
  }

  /** A self-signed certificate and its key in a PKCS#12 file, generated for this test only. */
  private Path certificate(final String password) throws Exception {
    final var file = tempDir.resolve("service-principal.pfx");
    final var keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
    final var process =
        new ProcessBuilder(
                keytool,
                "-genkeypair",
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-alias",
                "sp",
                "-dname",
                "CN=sp",
                "-validity",
                "1",
                "-storetype",
                "PKCS12",
                "-keystore",
                file.toString(),
                "-storepass",
                password)
            .redirectErrorStream(true)
            .start();
    final var output = new String(process.getInputStream().readAllBytes());
    assertThat(process.waitFor()).as(output).isZero();
    return file;
  }

  private Configuration workloadIdentityEnvironment(final String clientId, final String token)
      throws Exception {
    final var tokenFile = Files.writeString(tempDir.resolve("azure-identity-token"), token);
    return environment(
        Map.of(
            "AZURE_TENANT_ID",
            "tenant-id",
            "AZURE_CLIENT_ID",
            clientId,
            "AZURE_FEDERATED_TOKEN_FILE",
            tokenFile.toString()));
  }
}
