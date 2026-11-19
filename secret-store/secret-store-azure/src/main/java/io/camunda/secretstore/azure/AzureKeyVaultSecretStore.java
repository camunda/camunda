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
import com.azure.core.http.policy.ExponentialBackoffOptions;
import com.azure.core.http.policy.RetryOptions;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
 * <p>In one-by-one mode, {@link #resolve} reads each reference's latest version from the vault;
 * container mode and {@link #list} are not implemented yet (camunda/camunda#67226): they report
 * every name as not found and list no names. The store keeps no cache: the registry wraps it in
 * {@link io.camunda.secretstore.CachingSecretStore} and {@link
 * io.camunda.secretstore.ConcurrentSecretStore}, which fans a request out one name per call.
 *
 * <p>Each {@link #resolve} or {@link #list} call is bounded by {@code callTimeout}, retries
 * included: it runs on the credential executor, and the caller stops waiting past that time and
 * requests an interrupt, so a slow vault cannot stall the IO thread resolution shares with the
 * exporters (camunda/camunda#62869). Within it, each HTTP attempt of the default HTTP client is
 * bounded by {@code attemptTimeout} (an injected client keeps its own timeouts), and the SDK
 * retries up to {@code maxRetries} times on transient failures only (408, 429, 500, 502 to 504,
 * network errors and timeouts), honouring {@code Retry-After}. The backoff between retries is
 * scaled to fit in half of {@code callTimeout}, so a vault that keeps failing fast is reported with
 * its own answer rather than as a timeout.
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
 *   <tr><td>400, or a name Key Vault cannot hold</td>
 *       <td>{@link io.camunda.secretstore.SecretErrorCode#INVALID_REF INVALID_REF}</td></tr>
 *   <tr><td>401, 408, 429, 5xx, any other status, no response (a failed sign-in or a network
 *       error), past {@code callTimeout}</td>
 *       <td>{@link io.camunda.secretstore.SecretStoreUnavailableException}</td></tr>
 * </table>
 *
 * <p>Every failure message carries Key Vault's own status, error code and message, never a value.
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
  static final Pattern NAME_PATTERN = Pattern.compile("[0-9A-Za-z-]{1,127}");

  // the SDK's own base delay, kept when it already fits
  private static final Duration SDK_BASE_DELAY = Duration.ofMillis(800);

  private final AzureSecretResolver resolver;
  private final SecretClient client;
  private final ExecutorService credentialExecutor;
  private final SignInMonitor signInMonitor;
  private final Duration callTimeout;

  AzureKeyVaultSecretStore(
      final AzureSecretResolver resolver,
      final SecretClient client,
      final ExecutorService credentialExecutor,
      final SignInMonitor signInMonitor,
      final Duration callTimeout) {
    this.resolver = resolver;
    this.client = client;
    this.credentialExecutor = credentialExecutor;
    this.signInMonitor = signInMonitor;
    this.callTimeout = callTimeout;
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
   *     null} for the SDK default
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
      final var credential =
          AzureCredentialChain.build(
              config, environment, httpClient, credentialExecutor, signInMonitor);
      final var client = buildClient(config, credential, httpClient);
      final var storeDescription =
          "Azure Key Vault store '%s' of physical tenant '%s' at '%s'"
              .formatted(storeId, physicalTenantId, config.vaultUrl());
      store =
          new AzureKeyVaultSecretStore(
              resolver(config, client, clock, storeDescription),
              client,
              credentialExecutor,
              signInMonitor,
              config.callTimeout());
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
      final String storeDescription) {
    final var configuredPrefix = config.pathPrefix();
    final var pathPrefix = configuredPrefix == null ? "" : configuredPrefix;
    final var containerSecretId = config.containerSecretId();
    if (containerSecretId == null) {
      return new OneByOneSecretResolver(client, pathPrefix, clock, storeDescription);
    }
    return new ContainerSecretResolver(client, pathPrefix + containerSecretId);
  }

  /**
   * Token acquisition, and the startup check, run on platform threads the store owns. The
   * resolution pool calls the store on virtual threads ({@link
   * io.camunda.secretstore.ConcurrentSecretStore}), and the SDK's shared pool can deadlock there
   * when every carrier is pinned (Azure/azure-sdk-for-java#46231), as the Azure backup store also
   * works around. The pool creates threads on demand, reaps them after 60s idle, and is shut down
   * in {@link #close()}.
   */
  static ExecutorService newCredentialExecutor() {
    return Executors.newCachedThreadPool(
        Thread.ofPlatform().daemon().name("secret-store-azure-credential-", 0).factory());
  }

  private static SecretClient buildClient(
      final AzureKeyVaultStoreConfig config,
      final TokenCredential credential,
      final @Nullable HttpClient httpClient) {
    // only transient failures are retried by the SDK's default condition; Retry-After is honoured
    final var retryOptions = new RetryOptions(backoff(config.maxRetries(), config.callTimeout()));
    final var attemptOptions =
        new HttpClientOptions()
            .setConnectTimeout(config.attemptTimeout())
            .setResponseTimeout(config.attemptTimeout())
            .setReadTimeout(config.attemptTimeout());
    return new SecretClientBuilder()
        .vaultUrl(config.vaultUrl())
        .credential(credential)
        .retryOptions(retryOptions)
        .httpClient(httpClient != null ? httpClient : HttpClient.createDefault(attemptOptions))
        .buildClient();
  }

  /**
   * The SDK's exponential backoff, its delays summing to at most half of {@code callTimeout}. The
   * SDK's defaults (0.8s doubling, 5.6s over three retries) would outlast the default 5s call
   * timeout, so a fast failing vault would be reported as a timeout, without its answer.
   */
  private static ExponentialBackoffOptions backoff(
      final int maxRetries, final Duration callTimeout) {
    final var options = new ExponentialBackoffOptions().setMaxRetries(maxRetries);
    if (maxRetries == 0) {
      return options;
    }
    final long budget = callTimeout.toNanos() / 2;
    // the n-th retry waits baseDelay * 2^(n-1), so all of them wait baseDelay * (2^maxRetries - 1)
    final long baseDelay =
        Math.max(
            1, Math.min(SDK_BASE_DELAY.toNanos(), (long) (budget / (Math.pow(2, maxRetries) - 1))));
    return options
        .setBaseDelay(Duration.ofNanos(baseDelay))
        .setMaxDelay(Duration.ofNanos(Math.max(baseDelay, budget)));
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
    return withinCallTimeout(
        "resolve " + names.size() + " secret references", () -> resolver.resolve(names));
  }

  @Override
  public List<String> list() {
    return withinCallTimeout("list its secrets", resolver::list);
  }

  /**
   * Runs {@code call} on the credential executor, as the SDK's synchronous calls take no overall
   * timeout, and stops waiting past {@link #callTimeout}, requesting an interrupt of the call.
   *
   * @param what what the call does, for the message; never the names, which the message would
   *     otherwise repeat for every reference of a large call
   * @throws SecretStoreUnavailableException past the call timeout, or if the store is closed
   */
  private <T> T withinCallTimeout(final String what, final Callable<T> call) {
    final Future<T> future;
    try {
      future = credentialExecutor.submit(call);
    } catch (final RejectedExecutionException e) {
      throw new SecretStoreUnavailableException(
          "Azure Key Vault store is closed: cannot " + what, e);
    }
    try {
      return future.get(callTimeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (final TimeoutException e) {
      future.cancel(true);
      throw new SecretStoreUnavailableException(
          "Azure Key Vault is unavailable: it did not "
              + what
              + " within the call timeout of "
              + callTimeout,
          e);
    } catch (final InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw new SecretStoreUnavailableException(
          "Interrupted while waiting for Azure Key Vault to " + what, e);
    } catch (final ExecutionException e) {
      final var cause = e.getCause();
      // rethrown from this thread, so the stack trace shows the caller rather than the pool's
      if (cause instanceof final SecretStoreUnavailableException unavailable) {
        throw new SecretStoreUnavailableException(
            Objects.requireNonNullElse(unavailable.getMessage(), "Azure Key Vault is unavailable"),
            unavailable);
      }
      if (cause instanceof final RuntimeException runtime) {
        throw runtime;
      }
      if (cause instanceof final Error error) {
        throw error;
      }
      throw new SecretStoreUnavailableException(
          "Azure Key Vault is unavailable: cannot " + what, cause);
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
