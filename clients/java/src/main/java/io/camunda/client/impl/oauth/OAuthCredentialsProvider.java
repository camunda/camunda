/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl.oauth;

import static java.lang.Math.toIntExact;
import static java.util.UUID.randomUUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.impl.CamundaClientCredentials;
import io.camunda.client.impl.util.SSLContextUtil;
import io.camunda.client.impl.util.VersionUtil;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.net.ssl.HttpsURLConnection;
import net.jcip.annotations.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is thread-safe in terms of the next: 1. If you are trying to modify headers of your
 * request from the several threads there would be sequential calls to the cache 2. If the cache
 * hasn't a valid token and you are calling from several threads there would be just one call to the
 * Auth server
 */
@ThreadSafe
public final class OAuthCredentialsProvider implements CredentialsProvider {
  private static final String HEADER_AUTH_KEY = "Authorization";
  private static final String JWT_ASSERTION_TYPE =
      "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

  private static final ObjectMapper JSON_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final ObjectReader CREDENTIALS_READER =
      JSON_MAPPER.readerFor(CamundaClientCredentials.class);
  private static final Logger LOG = LoggerFactory.getLogger(OAuthCredentialsProvider.class);
  private final URL authorizationServerUrl;
  private final String clientId;
  private final String clientSecret;
  private final String audience;
  private final String scope;
  private final String resource;
  private final Path keystorePath;
  private final String keystorePassword;
  private final String keystoreKeyPassword;
  private final Path truststorePath;
  private final String truststorePassword;
  private final OAuthCredentialsCache credentialsCache;
  private final Duration connectionTimeout;
  private final Duration readTimeout;
  private final Duration proactiveTokenRefreshThreshold;
  private final int tokenFetchMaxRetries;
  private final long tokenFetchInitialBackoffMs;
  private final double tokenFetchBackoffMultiplier;
  private final Set<Integer> tokenFetchRetryableStatusCodes;

  /**
   * Latched when the token endpoint returns an HTTP response whose status code is not configured as
   * retryable in {@code tokenFetchRetryableStatusCodes}. Once set, every subsequent token fetch
   * fails immediately without contacting the OIDC provider, until this client instance is
   * recreated.
   */
  private final AtomicReference<IOException> permanentFailure = new AtomicReference<>();

  // client assertion
  private final boolean clientAssertionEnabled;
  private final Path clientAssertionKeystorePath;
  private final String clientAssertionKeystorePassword;
  private final String clientAssertionKeystoreKeyAlias;
  private final String clientAssertionKeystoreKeyPassword;

  /**
   * Tracks an in-flight background token refresh. Uses an AtomicReference to ensure only one
   * background refresh runs at a time — subsequent requests that see the token is nearing expiry
   * will observe an existing future and skip triggering another refresh.
   */
  private final AtomicReference<CompletableFuture<Void>> backgroundRefresh =
      new AtomicReference<>();

  OAuthCredentialsProvider(final OAuthCredentialsProviderBuilder builder) {
    authorizationServerUrl = builder.getAuthorizationServer();
    keystorePath = builder.getKeystorePath();
    keystorePassword = builder.getKeystorePassword();
    keystoreKeyPassword = builder.getKeystoreKeyPassword();
    truststorePath = builder.getTruststorePath();
    truststorePassword = builder.getTruststorePassword();
    clientId = builder.getClientId();
    clientSecret = builder.getClientSecret();
    audience = builder.getAudience();
    scope = builder.getScope();
    resource = builder.getResource();
    credentialsCache = new OAuthCredentialsCache(builder.getCredentialsCache());
    connectionTimeout = builder.getConnectTimeout();
    readTimeout = builder.getReadTimeout();
    proactiveTokenRefreshThreshold = builder.getProactiveTokenRefreshThreshold();
    tokenFetchMaxRetries = builder.getTokenFetchMaxRetries();
    tokenFetchInitialBackoffMs = builder.getTokenFetchInitialBackoff().toMillis();
    tokenFetchBackoffMultiplier = builder.getTokenFetchBackoffMultiplier();
    tokenFetchRetryableStatusCodes = builder.getTokenFetchRetryableStatusCodes();
    clientAssertionEnabled = builder.clientAssertionEnabled();
    clientAssertionKeystorePath = builder.getClientAssertionKeystorePath();
    clientAssertionKeystorePassword = builder.getClientAssertionKeystorePassword();
    clientAssertionKeystoreKeyAlias = builder.getClientAssertionKeystoreKeyAlias();
    clientAssertionKeystoreKeyPassword = builder.getClientAssertionKeystoreKeyPassword();
  }

  public boolean isClientAssertionEnabled() {
    return clientAssertionEnabled;
  }

  /** Adds an access token to the Authorization header of a gRPC call. */
  @Override
  public void applyCredentials(final CredentialsApplier applier) throws IOException {
    final CamundaClientCredentials camundaClientCredentials =
        credentialsCache.computeIfMissingOrInvalid(
            clientId,
            this::fetchCredentials,
            this::triggerProactiveRefresh,
            proactiveTokenRefreshThreshold);

    String type = camundaClientCredentials.getTokenType();
    if (type == null || type.isEmpty()) {
      throw new IOException(
          String.format("Expected valid token type but was absent or invalid '%s'", type));
    }

    type = Character.toUpperCase(type.charAt(0)) + type.substring(1);
    applier.put(
        HEADER_AUTH_KEY, String.format("%s %s", type, camundaClientCredentials.getAccessToken()));
  }

  /**
   * Returns true if the request should be retried — meaning the token was refreshed (either by this
   * thread or a concurrent one) and a retry with the new token is worthwhile. Returns false if the
   * failure was not an auth error, or if the freshly fetched credentials are identical to the
   * cached ones (indicating the 401 was not caused by a stale token). Delegates to {@link
   * OAuthCredentialsCache#forceRefreshIfChanged} which is synchronized on the same monitor as
   * {@link OAuthCredentialsCache#computeIfMissingOrInvalid}, ensuring that concurrent 401 retries
   * coalesce into a single token refresh call.
   */
  @Override
  public boolean shouldRetryRequest(final StatusCode statusCode) {
    if (!statusCode.isUnauthorized()) {
      return false;
    }
    // Short-circuit if the token endpoint has already permanently failed: no point in attempting
    // another fetch, and skipping it preserves the "single ERROR at trip time" promise by avoiding
    // log spam from this method's IOException catch on every subsequent rejected request.
    if (permanentFailure.get() != null) {
      return false;
    }

    try {
      return credentialsCache.forceRefreshIfChanged(clientId, this::fetchCredentials);
    } catch (final IOException e) {
      LOG.error("Failed while fetching credentials for clientId={}: ", clientId, e);
      return false;
    }
  }

  /**
   * Triggers a background token refresh if one is not already in progress. The refresh runs
   * asynchronously on a daemon thread, so it does not block the calling thread. If the refresh
   * fails, the error is logged and the next call to {@link #applyCredentials} will fall back to the
   * synchronized path when the grace period eventually kicks in.
   *
   * <p>The token fetch (HTTP POST to the OAuth server) runs WITHOUT holding the cache monitor, so
   * concurrent {@link #applyCredentials} calls continue serving the current (still-valid) token
   * unblocked. Only the brief {@code put()+writeCache()} at the end acquires the monitor.
   */
  private void triggerProactiveRefresh() {
    // Atomically swap in a placeholder so only one thread wins the race.
    // updateAndGet returns the current value if a refresh is already in-flight,
    // or installs and returns a new future if the slot is empty/done.
    final CompletableFuture<Void> winner =
        backgroundRefresh.updateAndGet(
            existing -> {
              if (existing != null && !existing.isDone()) {
                // A refresh is already in progress — keep it, skip
                return existing;
              }
              // This thread wins: create and return the refresh future
              return CompletableFuture.runAsync(
                  () -> {
                    try {
                      LOG.info("Proactively refreshing OAuth token for client '{}'", clientId);
                      // Step 1: Fetch the token WITHOUT any lock — this is the slow part
                      // (HTTP round-trip to the OAuth server).
                      final CamundaClientCredentials freshCredentials = fetchCredentials();

                      // Step 2: Briefly acquire the cache monitor to atomically put + write.
                      // During the proactive window the token is still valid, so
                      // computeIfMissingOrInvalid() only holds the monitor for a quick
                      // read-check-return — no contention.
                      credentialsCache.putAndWrite(clientId, freshCredentials);
                      LOG.info("Proactively refreshed OAuth token for client '{}'", clientId);
                    } catch (final IOException e) {
                      LOG.warn(
                          "Background OAuth token refresh failed for client '{}': {}",
                          clientId,
                          e.getMessage());
                    }
                  });
            });
    // winner is either the existing in-flight future or the one we just created — nothing to do
  }

  private String createPayload() {
    final Map<String, String> payload = new HashMap<>();
    if (clientAssertionEnabled) {
      payload.put("client_assertion", getClientAssertion());
      payload.put("client_assertion_type", JWT_ASSERTION_TYPE);
    } else {
      payload.put("client_secret", clientSecret);
    }

    payload.put("client_id", clientId);
    payload.put("audience", audience);
    payload.put("grant_type", "client_credentials");
    if (scope != null) {
      payload.put("scope", scope);
    }
    if (resource != null) {
      payload.put("resource", resource);
    }

    return payload.entrySet().stream()
        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
        .collect(Collectors.joining("&"));
  }

  private static String encode(final String param) {
    try {
      return URLEncoder.encode(param, StandardCharsets.UTF_8.name());
    } catch (final UnsupportedEncodingException e) {
      throw new UncheckedIOException("Failed while encoding OAuth request parameters: ", e);
    }
  }

  private CamundaClientCredentials fetchCredentials() throws IOException {
    final IOException latched = permanentFailure.get();
    if (latched != null) {
      LOG.debug(
          "Rejecting token fetch: OAuth credentials provider is permanently disabled", latched);
      throw new IOException(
          "OAuth credentials provider permanently disabled due to earlier non-retryable token "
              + "endpoint response. Restart the client after fixing the OAuth configuration.",
          latched);
    }

    IOException lastException = null;
    long backoffMs = tokenFetchInitialBackoffMs;

    for (int attempt = 1; attempt <= tokenFetchMaxRetries; attempt++) {
      try {
        return doFetchCredentials();
      } catch (final PermanentOAuthFailureException e) {
        // already latched inside doFetchCredentials; surface the underlying IOException
        throw (IOException) e.getCause();
      } catch (final IOException e) {
        lastException = e;
        if (attempt < tokenFetchMaxRetries) {
          // Honor a server-provided Retry-After hint when present: sleep at least that long but
          // not less than our computed backoff. Otherwise fall back to jittered exponential.
          final Long retryAfterMs =
              (e instanceof RetryableOAuthFailureException)
                  ? ((RetryableOAuthFailureException) e).retryAfterMs()
                  : null;
          final long sleepMs =
              retryAfterMs != null ? Math.max(retryAfterMs, backoffMs) : withEqualJitter(backoffMs);
          LOG.warn(
              "Token fetch failed for clientId={} (attempt {}/{}), retrying in {}ms{}: {}",
              clientId,
              attempt,
              tokenFetchMaxRetries,
              sleepMs,
              retryAfterMs != null ? " (honoring Retry-After)" : "",
              e.getMessage());
          try {
            Thread.sleep(sleepMs);
          } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting to retry token fetch", ie);
          }
          backoffMs = (long) (backoffMs * tokenFetchBackoffMultiplier);
        }
      }
    }

    LOG.warn(
        "Token fetch failed for clientId={} after {} attempts; surfacing last error to caller.",
        clientId,
        tokenFetchMaxRetries);
    throw lastException;
  }

  private CamundaClientCredentials doFetchCredentials() throws IOException {
    final HttpURLConnection connection =
        (HttpURLConnection) authorizationServerUrl.openConnection();
    maybeConfigureCustomSSLContext(connection);
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
    connection.setRequestProperty("Accept", "application/json");
    connection.setDoOutput(true);
    connection.setReadTimeout(toIntExact(readTimeout.toMillis()));
    connection.setConnectTimeout(toIntExact(connectionTimeout.toMillis()));
    connection.setRequestProperty("User-Agent", "camunda-client-java/" + VersionUtil.getVersion());

    try (final OutputStream os = connection.getOutputStream()) {
      final byte[] input = createPayload().getBytes(StandardCharsets.UTF_8);
      os.write(input, 0, input.length);
    }

    final int statusCode = connection.getResponseCode();
    if (statusCode != 200) {
      final IOException error =
          new IOException(
              String.format(
                  "Failed while requesting access token with status code %d and message %s.",
                  statusCode, connection.getResponseMessage()));
      if (!tokenFetchRetryableStatusCodes.contains(statusCode)) {
        if (permanentFailure.compareAndSet(null, error)) {
          LOG.error(
              "OAuth credentials provider permanently disabled after non-retryable HTTP {} from "
                  + "token endpoint {}. This client instance will reject all future token "
                  + "requests until restarted. Verify clientId, clientSecret, audience, and token "
                  + "URL configuration.",
              statusCode,
              authorizationServerUrl);
        }
        throw new PermanentOAuthFailureException(error);
      }
      throw new RetryableOAuthFailureException(error, parseRetryAfterMs(connection));
    }

    try (final InputStream in = connection.getInputStream();
        final InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
      final CamundaClientCredentials fetchedCredentials = CREDENTIALS_READER.readValue(reader);

      if (fetchedCredentials == null) {
        throw new IOException("Expected valid credentials but got null instead.");
      }

      return fetchedCredentials;
    }
  }

  private void maybeConfigureCustomSSLContext(final HttpURLConnection connection) {
    if (connection instanceof HttpsURLConnection) {
      final HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
      httpsConnection.setSSLSocketFactory(
          SSLContextUtil.createSSLFactory(
              keystorePath,
              keystorePassword,
              truststorePath,
              truststorePassword,
              keystoreKeyPassword));
    }
  }

  private String getClientAssertion() {
    final X509Certificate certificate;
    final Algorithm algorithm;
    try (final FileInputStream stream = new FileInputStream(clientAssertionKeystorePath.toFile())) {
      final KeyStore keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(stream, clientAssertionKeystorePassword.toCharArray());

      final RSAPrivateKey privateKey =
          (RSAPrivateKey)
              keyStore.getKey(
                  clientAssertionKeystoreKeyAlias,
                  clientAssertionKeystoreKeyPassword.toCharArray());
      final X509Certificate keyStoreCertificate =
          (X509Certificate) keyStore.getCertificate(clientAssertionKeystoreKeyAlias);
      final RSAPublicKey publicKey = (RSAPublicKey) keyStoreCertificate.getPublicKey();

      certificate = (X509Certificate) keyStore.getCertificate(clientAssertionKeystoreKeyAlias);
      algorithm = Algorithm.RSA256(publicKey, privateKey);
    } catch (final IOException | GeneralSecurityException e) {
      throw new RuntimeException("Failed to create client assertion", e);
    }

    final Date now = new Date();
    final String x5t = generateX5tThumbprint(certificate);

    final Map<String, Object> header = new HashMap<>();
    header.put("alg", "RSA256");
    header.put("typ", "JWT");
    header.put("x5t", x5t);

    return JWT.create()
        .withHeader(header)
        .withIssuer(clientId)
        .withSubject(clientId)
        .withAudience(authorizationServerUrl.toString())
        .withIssuedAt(now)
        .withNotBefore(now)
        .withExpiresAt(new Date(now.getTime() + 5 * 60 * 1000))
        .withJWTId(randomUUID().toString())
        .sign(algorithm);
  }

  private static String generateX5tThumbprint(final X509Certificate certificate) {
    try {
      final MessageDigest digest = MessageDigest.getInstance("SHA-1");
      final byte[] encoded = digest.digest(certificate.getEncoded());
      return Base64.getUrlEncoder().withoutPadding().encodeToString(encoded);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to generate x5t thumbprint", e);
    }
  }

  /**
   * Parses the {@code Retry-After} response header per RFC 7231 §7.1.3. Supports both the
   * delta-seconds form ({@code Retry-After: 120}) and the HTTP-date form ({@code Retry-After: Fri,
   * 31 Dec 1999 23:59:59 GMT}). Returns {@code null} if the header is absent, empty, or
   * unparseable.
   */
  private static Long parseRetryAfterMs(final HttpURLConnection connection) {
    final String header = connection.getHeaderField("Retry-After");
    if (header == null) {
      return null;
    }
    final String trimmed = header.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    try {
      final long seconds = Long.parseLong(trimmed);
      if (seconds < 0) {
        return null;
      }
      // Guard against overflow: cap at Long.MAX_VALUE / 1000 before multiplying.
      return seconds > Long.MAX_VALUE / 1000 ? Long.MAX_VALUE : seconds * 1000L;
    } catch (final NumberFormatException ignored) {
      // fall through to HTTP-date parsing
    }
    try {
      final ZonedDateTime when = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
      final long deltaMs = Duration.between(ZonedDateTime.now(when.getZone()), when).toMillis();
      return deltaMs < 0 ? 0L : deltaMs;
    } catch (final Exception ignored) {
      return null;
    }
  }

  /**
   * Applies equal jitter to a backoff value: returns a random value in {@code [baseMs/2, baseMs]}.
   * This prevents coordinated retry storms when many clients (e.g. pods restarting after a
   * deployment) hit the OAuth endpoint at the same time, which is exactly the scenario this
   * provider's retry logic is meant to dampen. See AWS Architecture Blog: "Exponential Backoff And
   * Jitter".
   */
  private static long withEqualJitter(final long baseMs) {
    if (baseMs <= 1) {
      return baseMs;
    }
    final long half = baseMs / 2;
    return half + ThreadLocalRandom.current().nextLong(baseMs - half + 1);
  }

  /**
   * Internal marker thrown by {@link #doFetchCredentials()} when a non-retryable HTTP response is
   * received, so the retry loop in {@link #fetchCredentials()} can distinguish it from a retryable
   * IOException without re-inspecting the underlying cause. Never escapes this class.
   */
  private static final class PermanentOAuthFailureException extends IOException {
    PermanentOAuthFailureException(final IOException cause) {
      super(cause);
    }
  }

  /**
   * Internal marker thrown by {@link #doFetchCredentials()} when a retryable HTTP response is
   * received, optionally carrying the {@code Retry-After} hint parsed from the response headers so
   * the retry loop can honor it. Never escapes this class.
   */
  private static final class RetryableOAuthFailureException extends IOException {
    private final Long retryAfterMs;

    RetryableOAuthFailureException(final IOException cause, final Long retryAfterMs) {
      super(cause);
      this.retryAfterMs = retryAfterMs;
    }

    Long retryAfterMs() {
      return retryAfterMs;
    }
  }
}
