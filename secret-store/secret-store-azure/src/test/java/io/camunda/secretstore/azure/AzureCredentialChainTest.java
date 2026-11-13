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
import com.azure.core.util.Configuration;
import io.camunda.secretstore.azure.StubAzureHttpClient.Request;
import io.camunda.zeebe.test.util.logging.LogCapturer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.apache.logging.log4j.Level;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AzureCredentialChainTest {

  private static final String VAULT_URL = "https://my-vault.vault.azure.net";
  private static final String US_GOVERNMENT_AUTHORITY = "https://login.microsoftonline.us/";
  private static final TokenRequestContext VAULT_SCOPE =
      new TokenRequestContext().addScopes("https://vault.azure.net/.default");

  private final ExecutorService executor = AzureKeyVaultSecretStore.newCredentialExecutor();

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
            new SignInMonitor(VAULT_URL));

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
    final var token = credential.getTokenSync(VAULT_SCOPE);

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
  void shouldSignInWithWorkloadIdentityAsTheConfiguredIdentity() throws Exception {
    // given
    final var http = StubAzureHttpClient.signingIn(3600);
    final var credential =
        chain(
            config("tenant-identity", null),
            workloadIdentityEnvironment("pod-client-id", "federated-token"),
            http);

    // when
    final var token = credential.getTokenSync(VAULT_SCOPE);

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
    final var token = credential.getTokenSync(VAULT_SCOPE);

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
    credential.getTokenSync(VAULT_SCOPE);

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
  void shouldSignEachPhysicalTenantInAsItsOwnIdentity() {
    // given — the same process environment, as two tenants of one broker share it
    final var tenantA = StubAzureHttpClient.signingIn(3600);
    final var tenantB = StubAzureHttpClient.signingIn(3600);
    final var credentialA = chain(config("identity-a", null), environment(Map.of()), tenantA);
    final var credentialB = chain(config("identity-b", null), environment(Map.of()), tenantB);

    // when
    credentialA.getTokenSync(VAULT_SCOPE);
    credentialB.getTokenSync(VAULT_SCOPE);

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
    credential.getTokenSync(VAULT_SCOPE);

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
    credential.getTokenSync(VAULT_SCOPE);

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
    assertThatThrownBy(() -> credential.getTokenSync(VAULT_SCOPE))
        .isInstanceOf(ClientAuthenticationException.class);
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
    assertThatThrownBy(() -> credential.getTokenSync(VAULT_SCOPE))
        .isInstanceOf(ClientAuthenticationException.class);
    assertThat(http.requests()).noneMatch(Request::isImdsRequest);
  }

  @Test
  void shouldLogOnceWhichCredentialSignedInWithoutTheTokenOrSecret() {
    // given
    final var http = StubAzureHttpClient.signingIn(3600);
    final var credential =
        chain(
            AzureKeyVaultStoreConfig.of(VAULT_URL),
            servicePrincipalEnvironment("sp-client-id", "sp-secret"),
            http);

    try (final var log = LogCapturer.capturing(SignInMonitor.class, Level.INFO)) {
      // when
      credential.getTokenSync(VAULT_SCOPE);
      credential.getTokenSync(VAULT_SCOPE);

      // then
      assertThat(log.messages())
          .singleElement()
          .asString()
          .contains(VAULT_URL)
          .contains(AzureCredentialChain.ENVIRONMENT)
          .contains("sp-client-id")
          .doesNotContain("sp-secret")
          .doesNotContain(StubAzureHttpClient.TOKEN);
    }
  }

  private TokenCredential chain(
      final AzureKeyVaultStoreConfig config,
      final Configuration environment,
      final StubAzureHttpClient http) {
    return AzureCredentialChain.build(
        config, environment, http, executor, new SignInMonitor(config.vaultUrl()));
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
