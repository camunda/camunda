/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import com.azure.core.credential.TokenCredential;
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
 *       {@code AZURE_USERNAME} and {@code AZURE_PASSWORD} are ignored: they sign in as a person.
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
    return chain.build();
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
    if (!isServicePrincipalConfigured(environment)) {
      // without a complete service principal the SDK falls back to AZURE_USERNAME and
      // AZURE_PASSWORD, which would sign in as a person rather than an application identity
      return unavailable(
          ENVIRONMENT
              + " authentication unavailable: it needs AZURE_TENANT_ID, AZURE_CLIENT_ID and"
              + " AZURE_CLIENT_SECRET or AZURE_CLIENT_CERTIFICATE_PATH");
    }
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
    if (!isWorkloadIdentityConfigured(config, environment)) {
      // the builder refuses to build without these; checking them here rather than catching its
      // IllegalArgumentException keeps a malformed value, such as an invalid tenant ID, a failure
      return unavailable(
          WORKLOAD_IDENTITY
              + " authentication unavailable: it needs AZURE_TENANT_ID, a client ID and "
              + AZURE_FEDERATED_TOKEN_FILE
              + ", which AKS sets only in pods labelled azure.workload.identity/use=true");
    }
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
    return builder.build();
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

  private static boolean isServicePrincipalConfigured(final Configuration environment) {
    return isSet(environment, Configuration.PROPERTY_AZURE_TENANT_ID)
        && isSet(environment, Configuration.PROPERTY_AZURE_CLIENT_ID)
        && (isSet(environment, Configuration.PROPERTY_AZURE_CLIENT_SECRET)
            || isSet(environment, Configuration.PROPERTY_AZURE_CLIENT_CERTIFICATE_PATH));
  }

  private static boolean isWorkloadIdentityConfigured(
      final AzureKeyVaultStoreConfig config, final Configuration environment) {
    return isSet(environment, Configuration.PROPERTY_AZURE_TENANT_ID)
        && isSet(environment, AZURE_FEDERATED_TOKEN_FILE)
        && (config.managedIdentityClientId() != null
            || isSet(environment, Configuration.PROPERTY_AZURE_CLIENT_ID));
  }

  private static boolean isSet(final Configuration environment, final String name) {
    final var value = environment.get(name);
    return value != null && !value.isBlank();
  }

  private static TokenCredential unavailable(final String reason) {
    return request -> Mono.error(new CredentialUnavailableException(reason));
  }

  private static String clientIdSuffix(final @Nullable String clientId) {
    return clientId == null || clientId.isBlank() ? "" : ", client ID " + clientId;
  }
}
