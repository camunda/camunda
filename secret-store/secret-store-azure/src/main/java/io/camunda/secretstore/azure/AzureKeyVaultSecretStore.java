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
import com.azure.core.http.policy.RetryPolicy;
import com.azure.core.util.Configuration;
import com.azure.core.util.HttpClientOptions;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreUnavailableException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A {@link SecretStore} backed by Azure Key Vault.
 *
 * <p>How a reference is looked up is delegated to one {@link AzureSecretResolver}, chosen once at
 * construction from {@link AzureKeyVaultStoreConfig#containerSecretId()}: {@link
 * ContainerSecretResolver} when it is set (every reference is a key of one JSON secret), {@link
 * OneByOneSecretResolver} otherwise (one Key Vault secret per reference). Adding a resolution mode
 * means adding a resolver, not touching this class.
 *
 * <p>Each instance signs in with its own {@link AzureCredentialChain credential chain} and talks to
 * the vault through its own {@link SecretClient}, so two physical tenants never share an identity
 * or a cached token. No credential is read from the configuration.
 *
 * <p>In one-by-one mode, {@link #resolve} reads each reference's latest version from the vault, and
 * {@link #list} pages through the vault's secret properties, never a value, listing every secret
 * under the path prefix whatever its state. Container mode is not implemented yet
 * (camunda/camunda#67226): it reports every name as not found and lists no names. The store keeps
 * no cache: the registry wraps it in {@link io.camunda.secretstore.CachingSecretStore} and, with a
 * {@code max-concurrency} above 1, {@link io.camunda.secretstore.ConcurrentSecretStore}, which fans
 * a request out one name per call.
 *
 * <p>Each read of the vault is bounded by {@code callTimeout}, retries included: it runs on the
 * credential executor, and the caller stops waiting past that time and requests an interrupt, so a
 * slow vault cannot stall the IO thread resolution shares with the exporters
 * (camunda/camunda#62869) for longer than that ({@link CallTimeout}). Within it, each HTTP attempt
 * of the default HTTP client to the vault is bounded by {@code attemptTimeout}; one to Microsoft
 * Entra ID or IMDS connects within {@code attemptTimeout} but may wait up to {@code callTimeout}
 * for its answer, for a managed identity's slow first token (an injected client keeps its own
 * timeouts). The SDK retries up to {@code maxRetries} times on transient failures only: 408, 429,
 * 500, 502 to 504, network errors and timeouts, never a rejected sign-in ({@link
 * KeyVaultRetryStrategy}). The waits between retries, backoff and {@code Retry-After} alike, fit in
 * half of {@code callTimeout}, so a vault that keeps failing fast or throttling is reported with
 * its own answer rather than as a timeout; a retry whose wait would not fit is not made.
 *
 * <p>Key Vault's answers map to the shared error codes as follows ({@link AzureKeyVaultErrors}):
 *
 * <table>
 *   <caption>Key Vault answer to error code</caption>
 *   <tr><th>Answer</th><th>Result</th></tr>
 *   <tr><td>404</td><td>{@link io.camunda.secretstore.SecretErrorCode#NOT_FOUND NOT_FOUND}</td></tr>
 *   <tr><td>403 with inner error code {@code SecretDisabled}, or an expired or not yet active
 *       secret</td><td>{@link io.camunda.secretstore.SecretErrorCode#NOT_FOUND NOT_FOUND}</td></tr>
 *   <tr><td>403</td>
 *       <td>{@link io.camunda.secretstore.SecretErrorCode#ACCESS_DENIED ACCESS_DENIED}</td></tr>
 *   <tr><td>403 with inner error code {@code ForbiddenByFirewall} or {@code ForbiddenByConnection}
 *       (the vault's network rules refused the caller)</td>
 *       <td>{@link io.camunda.secretstore.SecretStoreUnavailableException}</td></tr>
 *   <tr><td>400, or a name Key Vault cannot hold</td>
 *       <td>{@link io.camunda.secretstore.SecretErrorCode#INVALID_REF INVALID_REF}</td></tr>
 *   <tr><td>401, 408, 429, 5xx, any other status, no response (a failed sign-in or a network
 *       error), past {@code callTimeout}</td>
 *       <td>{@link io.camunda.secretstore.SecretStoreUnavailableException}</td></tr>
 * </table>
 *
 * <p>Any failure to list is reported as {@link
 * io.camunda.secretstore.SecretStoreUnavailableException}, naming the missing {@code list}
 * permission on a 403.
 *
 * <p>A failure message carries Key Vault's own status, error code and message when the vault
 * answered, and the network error otherwise, never a value.
 *
 * <p>This class is thread-safe: its resolvers keep no mutable state, and its {@link SignInMonitor}
 * is thread-safe.
 */
public final class AzureKeyVaultSecretStore implements SecretStore {

  /**
   * The Key Vault secret names a reference may map to: 1 to 127 letters, digits and hyphens, path
   * prefix included. Deliberately stricter than the reference names Camunda accepts, so it is not
   * part of the "same charset" check across stores: in one-by-one mode a reference outside it, such
   * as {@code API_KEY}, is refused rather than rewritten.
   */
  static final Pattern NAME_PATTERN =
      Pattern.compile(
          AzureKeyVaultStoreConfig.SECRET_NAME_CHAR
              + "{1,"
              + AzureKeyVaultStoreConfig.MAX_SECRET_NAME_LENGTH
              + "}");

  private final AzureSecretResolver resolver;
  private final SecretClient client;
  private final ExecutorService credentialExecutor;
  private final SignInMonitor signInMonitor;

  AzureKeyVaultSecretStore(
      final AzureSecretResolver resolver,
      final SecretClient client,
      final ExecutorService credentialExecutor,
      final SignInMonitor signInMonitor) {
    this.resolver = resolver;
    this.client = client;
    this.credentialExecutor = credentialExecutor;
    this.signInMonitor = signInMonitor;
  }

  /**
   * Builds a store from configuration, with the resolver its container secret setting selects. It
   * signs in with the {@code AZURE_*} variables of this process's environment.
   *
   * <p>Before returning, checks once that the store can reach its vault and sign in, logging the
   * outcome ({@link StartupCheck}). The store is returned whatever the outcome, within the call
   * timeout at most.
   *
   * @param physicalTenantId the physical tenant the store belongs to, named in its log lines
   * @param storeId the ID the store is configured under, tagging its meters
   * @param meterRegistry where the store publishes {@link
   *     io.camunda.secretstore.SecretStoreMetricsDoc#SIGNIN_FAILURES}; the caller tags it with the
   *     physical tenant. The meter is removed again on {@link #close()}.
   */
  public static AzureKeyVaultSecretStore fromConfig(
      final String physicalTenantId,
      final String storeId,
      final AzureKeyVaultStoreConfig config,
      final MeterRegistry meterRegistry) {
    return fromConfig(
        physicalTenantId,
        storeId,
        config,
        meterRegistry,
        Configuration.getGlobalConfiguration(),
        null);
  }

  /**
   * @param environment where sign-in reads the {@code AZURE_*} variables from
   * @param httpClient the client every call to Entra ID, IMDS and the vault goes through; {@code
   *     null} for the SDK default, each attempt bounded by {@code attemptTimeout}, or for sign-in
   *     answers by {@code callTimeout}
   */
  static AzureKeyVaultSecretStore fromConfig(
      final String physicalTenantId,
      final String storeId,
      final AzureKeyVaultStoreConfig config,
      final MeterRegistry meterRegistry,
      final Configuration environment,
      final @Nullable HttpClient httpClient) {
    return fromConfig(
        physicalTenantId,
        storeId,
        config,
        meterRegistry,
        environment,
        httpClient,
        InstantSource.system());
  }

  /**
   * @param clock what a secret's expiry and activation dates are compared with
   */
  static AzureKeyVaultSecretStore fromConfig(
      final String physicalTenantId,
      final String storeId,
      final AzureKeyVaultStoreConfig config,
      final MeterRegistry meterRegistry,
      final Configuration environment,
      final @Nullable HttpClient httpClient,
      final InstantSource clock) {
    final var credentialExecutor = newCredentialExecutor();
    final var signInMonitor =
        new SignInMonitor(physicalTenantId, storeId, config.vaultUrl(), meterRegistry);
    final AzureKeyVaultSecretStore store;
    try {
      final var vaultHttp =
          httpClient != null ? httpClient : defaultHttpClient(config, config.attemptTimeout());
      final var signInHttp =
          httpClient != null ? httpClient : defaultHttpClient(config, config.callTimeout());
      final var credential =
          AzureCredentialChain.build(
              config, environment, signInHttp, credentialExecutor, signInMonitor);
      final var client = buildClient(config, credential, vaultHttp);
      final var storeDescription =
          "Azure Key Vault store '%s' of physical tenant '%s' at '%s'"
              .formatted(storeId, physicalTenantId, config.vaultUrl());
      final var callTimeout = new CallTimeout(credentialExecutor, config.callTimeout());
      store =
          new AzureKeyVaultSecretStore(
              resolver(config, client, clock, storeDescription, callTimeout),
              client,
              credentialExecutor,
              signInMonitor);
    } catch (final RuntimeException e) {
      signInMonitor.close();
      credentialExecutor.shutdownNow();
      throw e;
    }
    StartupCheck.run(
        store.resolver,
        credentialExecutor,
        config.callTimeout(),
        signInMonitor,
        physicalTenantId,
        storeId,
        config.vaultUrl());
    return store;
  }

  private static AzureSecretResolver resolver(
      final AzureKeyVaultStoreConfig config,
      final SecretClient client,
      final InstantSource clock,
      final String storeDescription,
      final CallTimeout callTimeout) {
    final var configuredPrefix = config.pathPrefix();
    final var pathPrefix = configuredPrefix == null ? "" : configuredPrefix;
    final var containerSecretId = config.containerSecretId();
    if (containerSecretId == null) {
      return new OneByOneSecretResolver(client, pathPrefix, clock, storeDescription, callTimeout);
    }
    return new ContainerSecretResolver(client, pathPrefix + containerSecretId);
  }

  /**
   * Reads of the vault, token acquisition, and the startup check run on platform threads the store
   * owns. The resolution pool calls the store on virtual threads ({@link
   * io.camunda.secretstore.ConcurrentSecretStore}), and the SDK's shared pool can deadlock there
   * when every carrier is pinned (Azure/azure-sdk-for-java#46231), as the Azure backup store also
   * works around. The pool creates threads on demand, reaps them after 60s idle, and is shut down
   * in {@link #close()}.
   */
  static ExecutorService newCredentialExecutor() {
    return Executors.newCachedThreadPool(
        Thread.ofPlatform().daemon().name("secret-store-azure-credential-", 0).factory());
  }

  /**
   * The SDK's default client, connecting within {@code attemptTimeout} and waiting for an answer at
   * most {@code answerTimeout}. Sign-in waits up to {@code callTimeout} for an answer, as the first
   * token of a managed identity can take longer than one attempt to the vault, while an unreachable
   * endpoint still fails within {@code attemptTimeout}; the call timeout bounds both.
   */
  private static HttpClient defaultHttpClient(
      final AzureKeyVaultStoreConfig config, final Duration answerTimeout) {
    return HttpClient.createDefault(
        new HttpClientOptions()
            .setConnectTimeout(config.attemptTimeout())
            .setResponseTimeout(answerTimeout)
            .setReadTimeout(answerTimeout));
  }

  private static SecretClient buildClient(
      final AzureKeyVaultStoreConfig config,
      final TokenCredential credential,
      final HttpClient httpClient) {
    return new SecretClientBuilder()
        .vaultUrl(config.vaultUrl())
        .credential(credential)
        .retryPolicy(
            new RetryPolicy(new KeyVaultRetryStrategy(config.maxRetries(), config.callTimeout())))
        .httpClient(httpClient)
        .buildClient();
  }

  /** The client {@link #resolve} and {@link #list} read the vault with. */
  SecretClient client() {
    return client;
  }

  ExecutorService credentialExecutor() {
    return credentialExecutor;
  }

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    requireOpen("resolve secret references");
    return resolver.resolve(names);
  }

  @Override
  public List<String> list() {
    requireOpen("list its secrets");
    return resolver.list();
  }

  private void requireOpen(final String what) {
    if (credentialExecutor.isShutdown()) {
      throw new SecretStoreUnavailableException("Azure Key Vault store is closed: cannot " + what);
    }
  }

  @Override
  public int namesPerCall() {
    return resolver.namesPerCall();
  }

  @Override
  public void close() {
    // first, so a token request the shutdown interrupts is not reported as a failed sign-in
    signInMonitor.close();
    credentialExecutor.shutdownNow();
  }
}
