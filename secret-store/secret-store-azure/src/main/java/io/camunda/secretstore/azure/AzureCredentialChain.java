/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.http.HttpClient;
import com.azure.core.util.Configuration;
import com.azure.identity.ChainedTokenCredentialBuilder;
import com.azure.identity.CredentialUnavailableException;
import com.azure.identity.EnvironmentCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.identity.WorkloadIdentityCredentialBuilder;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

/**
 * Builds the credential an {@link AzureKeyVaultSecretStore} signs in with: an explicit chain of the
 * three production sign-in methods, tried in this order.
 *
 * <ol>
 *   <li>{@code EnvironmentCredential}: a service principal from {@code AZURE_TENANT_ID}, {@code
 *       AZURE_CLIENT_ID} and {@code AZURE_CLIENT_SECRET} or {@code AZURE_CLIENT_CERTIFICATE_PATH}.
 *   <li>{@code WorkloadIdentityCredential}: the federated token AKS workload identity mounts at
 *       {@code AZURE_FEDERATED_TOKEN_FILE}.
 *   <li>{@code ManagedIdentityCredential}: the VM's or pod's managed identity, through IMDS.
 * </ol>
 *
 * <p>{@code DefaultAzureCredential} is deliberately not used: after these it also tries the Azure
 * CLI, Azure Developer CLI, PowerShell and IDE logins, so on a machine where a person is logged in
 * the cluster would silently sign in as that person. With none of the three above available,
 * signing in fails instead.
 *
 * <p>The chain moves on to the next credential only when one is unavailable (not configured in this
 * environment). A credential that is configured but fails, for example a service principal with a
 * wrong secret, ends the chain with that failure rather than signing in as another identity.
 *
 * <p>{@link AzureKeyVaultStoreConfig#managedIdentityClientId()} selects the identity for workload
 * and managed identity, so each physical tenant can sign in as its own user-assigned identity. A
 * service principal is named by {@code AZURE_CLIENT_ID} instead, which every tenant in the process
 * shares. {@link AzureKeyVaultStoreConfig#authorityHost()} applies to the two credentials that sign
 * in through Microsoft Entra ID; managed identity gets its token from IMDS, which has no authority.
 *
 * <p>Tokens are neither cached nor refreshed here: the vault client's bearer-token policy caches
 * the token and refreshes it ahead of its expiry.
 */
final class AzureCredentialChain {

  static final String ENVIRONMENT = "EnvironmentCredential";
  static final String WORKLOAD_IDENTITY = "WorkloadIdentityCredential";
  static final String MANAGED_IDENTITY = "ManagedIdentityCredential";

  private static final String AZURE_FEDERATED_TOKEN_FILE = "AZURE_FEDERATED_TOKEN_FILE";

  private AzureCredentialChain() {}

  /**
   * @param environment where the credentials read the {@code AZURE_*} variables from
   * @param httpClient the client the credentials call Entra ID and IMDS with; {@code null} for the
   *     SDK default
   * @param executor runs token acquisition; owned and shut down by the caller
   */
  static TokenCredential build(
      final AzureKeyVaultStoreConfig config,
      final Configuration environment,
      final @Nullable HttpClient httpClient,
      final ExecutorService executor,
      final SignInMonitor monitor) {
    final var chain = new ChainedTokenCredentialBuilder();
    credentials(config, environment, httpClient, executor, monitor).forEach(chain::addLast);
    return new NoSignInMethodReporting(chain.build(), monitor);
  }

  /** The chain's credentials, in the order they are tried. */
  static List<SignInCredential> credentials(
      final AzureKeyVaultStoreConfig config,
      final Configuration environment,
      final @Nullable HttpClient httpClient,
      final ExecutorService executor,
      final SignInMonitor monitor) {
    final var clientId = config.managedIdentityClientId();
    final var environmentClientId = environment.get(Configuration.PROPERTY_AZURE_CLIENT_ID);
    return List.of(
        new SignInCredential(
            ENVIRONMENT,
            ENVIRONMENT + " (service principal" + clientIdSuffix(environmentClientId) + ")",
            environmentCredential(config, environment, httpClient, executor),
            monitor),
        new SignInCredential(
            WORKLOAD_IDENTITY,
            WORKLOAD_IDENTITY
                + " (workload identity"
                + clientIdSuffix(clientId != null ? clientId : environmentClientId)
                + ")",
            workloadIdentityCredential(config, environment, httpClient, executor),
            monitor),
        new SignInCredential(
            MANAGED_IDENTITY,
            MANAGED_IDENTITY
                + (clientId != null
                    ? " (user-assigned identity" + clientIdSuffix(clientId) + ")"
                    : " (system-assigned identity)"),
            managedIdentityCredential(config, environment, httpClient, executor),
            monitor));
  }

  private static TokenCredential environmentCredential(
      final AzureKeyVaultStoreConfig config,
      final Configuration environment,
      final @Nullable HttpClient httpClient,
      final ExecutorService executor) {
    final var builder =
        new EnvironmentCredentialBuilder().configuration(environment).executorService(executor);
    if (config.authorityHost() != null) {
      builder.authorityHost(config.authorityHost());
    }
    if (httpClient != null) {
      builder.httpClient(httpClient);
    }
    return builder.build();
  }

  private static TokenCredential workloadIdentityCredential(
      final AzureKeyVaultStoreConfig config,
      final Configuration environment,
      final @Nullable HttpClient httpClient,
      final ExecutorService executor) {
    final var builder =
        new WorkloadIdentityCredentialBuilder()
            .configuration(environment)
            .executorService(executor);
    if (config.managedIdentityClientId() != null) {
      builder.clientId(config.managedIdentityClientId());
    }
    if (config.authorityHost() != null) {
      builder.authorityHost(config.authorityHost());
    }
    if (httpClient != null) {
      builder.httpClient(httpClient);
    }
    try {
      return builder.build();
    } catch (final IllegalArgumentException notConfigured) {
      // unlike the other two, this builder refuses to build without its environment, so outside a
      // workload-identity pod it stands in as unavailable for the chain to move past
      final var reason =
          WORKLOAD_IDENTITY
              + " authentication unavailable: it needs AZURE_TENANT_ID, a client ID and "
              + AZURE_FEDERATED_TOKEN_FILE
              + ", which AKS sets only in pods labelled azure.workload.identity/use=true";
      return request -> Mono.error(new CredentialUnavailableException(reason));
    }
  }

  private static TokenCredential managedIdentityCredential(
      final AzureKeyVaultStoreConfig config,
      final Configuration environment,
      final @Nullable HttpClient httpClient,
      final ExecutorService executor) {
    final var builder =
        new ManagedIdentityCredentialBuilder().configuration(environment).executorService(executor);
    if (config.managedIdentityClientId() != null) {
      builder.clientId(config.managedIdentityClientId());
    }
    if (httpClient != null) {
      builder.httpClient(httpClient);
    }
    return builder.build();
  }

  private static String clientIdSuffix(final @Nullable String clientId) {
    return clientId == null || clientId.isBlank() ? "" : ", client ID " + clientId;
  }

  /**
   * Reports to the monitor when the chain fails because none of its credentials was available. A
   * credential that is available but fails is reported by its own {@link SignInCredential}, and the
   * chain then fails with a plain {@link com.azure.core.exception.ClientAuthenticationException}
   * that this ignores, so a failed token request is counted once either way.
   */
  private record NoSignInMethodReporting(TokenCredential chain, SignInMonitor monitor)
      implements TokenCredential {

    private static final String DESCRIPTION =
        "any production sign-in method ("
            + String.join(", ", ENVIRONMENT, WORKLOAD_IDENTITY, MANAGED_IDENTITY)
            + "), none of which is available";

    @Override
    public Mono<AccessToken> getToken(final TokenRequestContext request) {
      return chain.getToken(request).doOnError(this::report);
    }

    @Override
    public AccessToken getTokenSync(final TokenRequestContext request) {
      try {
        return chain.getTokenSync(request);
      } catch (final RuntimeException e) {
        report(e);
        throw e;
      }
    }

    private void report(final Throwable failure) {
      if (failure instanceof CredentialUnavailableException) {
        monitor.failed(DESCRIPTION, failure);
      }
    }
  }
}
