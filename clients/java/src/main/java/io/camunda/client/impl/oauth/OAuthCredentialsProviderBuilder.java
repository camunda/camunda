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

import static io.camunda.client.impl.BuilderUtils.applyEnvironmentValueIfNotNull;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_AUTHORIZATION_SERVER;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_CACHE_PATH;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_CLIENT_ASSERTION_KEYSTORE_KEY_ALIAS;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_CLIENT_ASSERTION_KEYSTORE_KEY_PASSWORD;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_CLIENT_ASSERTION_KEYSTORE_PASSWORD;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_CLIENT_ASSERTION_KEYSTORE_PATH;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_CLIENT_ID;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_CLIENT_SECRET;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_CONNECT_TIMEOUT;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_ISSUER_URL;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_PROACTIVE_TOKEN_REFRESH_THRESHOLD;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_READ_TIMEOUT;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_KEYSTORE_KEY_SECRET;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_KEYSTORE_PATH;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_KEYSTORE_SECRET;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_TRUSTSTORE_PATH;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_TRUSTSTORE_SECRET;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_TOKEN_AUDIENCE;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_TOKEN_FETCH_BACKOFF_MULTIPLIER;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_TOKEN_FETCH_INITIAL_BACKOFF;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_TOKEN_FETCH_MAX_RETRIES;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_TOKEN_RESOURCE;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_TOKEN_SCOPE;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_WELL_KNOWN_CONFIGURATION_URL;
import static java.lang.Math.toIntExact;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.camunda.client.impl.CamundaClientCredentials;
import io.camunda.client.impl.LegacyZeebeClientEnvironmentVariables;
import io.camunda.client.impl.util.SSLContextUtil;
import io.camunda.client.impl.util.VersionUtil;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.Objects;
import javax.net.ssl.HttpsURLConnection;

public final class OAuthCredentialsProviderBuilder {
  public static final String INVALID_ARGUMENT_MSG = "Expected valid %s but none was provided.";
  public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
  public static final Duration DEFAULT_READ_TIMEOUT = DEFAULT_CONNECT_TIMEOUT;
  public static final Duration DEFAULT_PROACTIVE_TOKEN_REFRESH_THRESHOLD = Duration.ofSeconds(30);
  public static final String DEFAULT_CREDENTIALS_CACHE_PATH =
      Paths.get(System.getProperty("user.home"), ".camunda", "credentials")
          .toAbsolutePath()
          .toString();
  public static final String DEFAULT_AUTHZ_SERVER = "https://login.cloud.camunda.io/oauth/token/";
  public static final int DEFAULT_TOKEN_FETCH_MAX_RETRIES = 5;
  public static final Duration DEFAULT_TOKEN_FETCH_INITIAL_BACKOFF = Duration.ofSeconds(1);
  public static final double DEFAULT_TOKEN_FETCH_BACKOFF_MULTIPLIER = 2.0;
  private String clientId;
  private String clientSecret;
  private String audience;
  private String scope;
  private String resource;
  private String authorizationServerUrl;
  private String wellKnownConfigurationUrl;
  private String issuerUrl;
  private URL authorizationServer;
  private Path keystorePath;
  private String keystorePassword;
  private String keystoreKeyPassword;
  private Path truststorePath;
  private String truststorePassword;
  private String credentialsCachePath;
  private File credentialsCache;
  private Duration connectTimeout;
  private Duration readTimeout;
  private Duration proactiveTokenRefreshThreshold;
  private Integer tokenFetchMaxRetries;
  private Duration tokenFetchInitialBackoff;
  private Double tokenFetchBackoffMultiplier;
  private boolean applyEnvironmentOverrides = true;
  private Path clientAssertionKeystorePath;
  private String clientAssertionKeystorePassword;
  private String clientAssertionKeystoreKeyAlias;
  private String clientAssertionKeystoreKeyPassword;

  /** Client id to be used when requesting access token from OAuth authorization server. */
  public OAuthCredentialsProviderBuilder clientId(final String clientId) {
    this.clientId = clientId;
    return this;
  }

  /**
   * @see OAuthCredentialsProviderBuilder#clientId(String)
   */
  String getClientId() {
    return clientId;
  }

  /** Client secret to be used when requesting access token from OAuth authorization server. */
  public OAuthCredentialsProviderBuilder clientSecret(final String clientSecret) {
    this.clientSecret = clientSecret;
    return this;
  }

  /**
   * @see OAuthCredentialsProviderBuilder#clientSecret(String)
   */
  String getClientSecret() {
    return clientSecret;
  }

  /** The resource for which the access token should be valid. */
  public OAuthCredentialsProviderBuilder audience(final String audience) {
    this.audience = audience;
    return this;
  }

  /**
   * @see OAuthCredentialsProviderBuilder#audience(String)
   */
  String getAudience() {
    return audience;
  }

  /** The scopes of the access token. */
  public OAuthCredentialsProviderBuilder scope(final String scope) {
    this.scope = scope;
    return this;
  }

  /**
   * @see OAuthCredentialsProviderBuilder#scope(String)
   */
  String getScope() {
    return scope;
  }

  /** The resource for which the access token should be valid. */
  public OAuthCredentialsProviderBuilder resource(final String resource) {
    this.resource = resource;
    return this;
  }

  /**
   * @see OAuthCredentialsProviderBuilder#resource(String)
   */
  public String getResource() {
    return resource;
  }

  /** The authorization server's URL, from which the access token will be requested. */
  public OAuthCredentialsProviderBuilder authorizationServerUrl(
      final String authorizationServerUrl) {
    this.authorizationServerUrl = authorizationServerUrl;
    return this;
  }

  /** The well known configuration URL of the issuer that will issue the access token. */
  public OAuthCredentialsProviderBuilder wellKnownConfigurationUrl(
      final String wellKnownConfigurationUrl) {
    this.wellKnownConfigurationUrl = wellKnownConfigurationUrl;
    return this;
  }

  /** The URL of the issuer that will issue the access token. */
  public OAuthCredentialsProviderBuilder issuerUrl(final String issuerUrl) {
    this.issuerUrl = issuerUrl;
    return this;
  }

  /**
   * @see OAuthCredentialsProviderBuilder#authorizationServerUrl(String)
   */
  public URL getAuthorizationServer() {
    return authorizationServer;
  }

  /** Path to keystore used for OAuth identity provider */
  public OAuthCredentialsProviderBuilder keystorePath(final Path keystorePath) {
    this.keystorePath = keystorePath;
    return this;
  }

  private OAuthCredentialsProviderBuilder keystorePath(final String keystorePath) {
    if (keystorePath != null) {
      return keystorePath(Paths.get(keystorePath));
    }
    return this;
  }

  /**
   * @see OAuthCredentialsProviderBuilder#keystorePath(Path)
   */
  Path getKeystorePath() {
    return keystorePath;
  }

  /** Password to keystore used for OAuth identity provider */
  public OAuthCredentialsProviderBuilder keystorePassword(final String keystorePassword) {
    this.keystorePassword = keystorePassword;
    return this;
  }

  /**
   * @see OAuthCredentialsProviderBuilder#keystorePassword(String)
   */
  String getKeystorePassword() {
    return keystorePassword;
  }

  /** Keystore key password used for OAuth identity provider */
  public OAuthCredentialsProviderBuilder keystoreKeyPassword(final String keystoreKeyPassword) {
    this.keystoreKeyPassword = keystoreKeyPassword;
    return this;
  }

  /**
   * @see OAuthCredentialsProviderBuilder#keystoreKeyPassword(String)
   */
  String getKeystoreKeyPassword() {
    return keystoreKeyPassword;
  }

  /** Path to truststore used for OAuth identity provider */
  public OAuthCredentialsProviderBuilder truststorePath(final Path truststorePath) {
    this.truststorePath = truststorePath;
    return this;
  }

  private OAuthCredentialsProviderBuilder truststorePath(final String truststorePath) {
    if (truststorePath != null) {
      return truststorePath(Paths.get(truststorePath));
    }
    return this;
  }

  /**
   * @see OAuthCredentialsProviderBuilder#truststorePath(Path)
   */
  Path getTruststorePath() {
    return truststorePath;
  }

  /** Password to truststore used for OAuth identity provider */
  public OAuthCredentialsProviderBuilder truststorePassword(final String truststorePassword) {
    this.truststorePassword = truststorePassword;
    return this;
  }

  /**
   * @see OAuthCredentialsProviderBuilder#truststorePassword(String)
   */
  String getTruststorePassword() {
    return truststorePassword;
  }

  /**
   * The location for the credentials cache file. If none (or null) is specified the default will be
   * $HOME/.camunda/credentials
   */
  public OAuthCredentialsProviderBuilder credentialsCachePath(final String cachePath) {
    credentialsCachePath = cachePath;
    return this;
  }

  /**
   * @see OAuthCredentialsProviderBuilder#credentialsCachePath(String)
   */
  File getCredentialsCache() {
    return credentialsCache;
  }

  /**
   * The connection timeout of requests to the OAuth credentials provider. The default value is 5
   * seconds. Max value is {@link Integer#MAX_VALUE} milliseconds.
   */
  public OAuthCredentialsProviderBuilder connectTimeout(final Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
    return this;
  }

  private OAuthCredentialsProviderBuilder connectTimeout(final String connectTimeout) {
    if (connectTimeout != null) {
      return connectTimeout(Duration.ofMillis(Long.parseLong(connectTimeout)));
    }
    return this;
  }

  /**
   * @see #connectTimeout(Duration)
   */
  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  /**
   * The data read timeout of requests to the OAuth credentials provider. The default value is 5
   * seconds. Max value is {@link Integer#MAX_VALUE} milliseconds.
   */
  public OAuthCredentialsProviderBuilder readTimeout(final Duration readTimeout) {
    this.readTimeout = readTimeout;
    return this;
  }

  private OAuthCredentialsProviderBuilder readTimeout(final String readTimeout) {
    if (readTimeout != null) {
      return readTimeout(Duration.ofMillis(Long.parseLong(readTimeout)));
    }
    return this;
  }

  /**
   * @see #readTimeout(Duration)
   */
  public Duration getReadTimeout() {
    return readTimeout;
  }

  /**
   * Window before actual token expiry during which a background refresh is triggered eagerly. The
   * token remains valid inside this window; this is purely a policy knob controlling how early
   * refresh kicks in, so concurrent callers don't have to block on a synchronous refresh at the
   * cliff edge. Must be strictly larger than {@link CamundaClientCredentials#EXPIRY_GRACE_PERIOD}.
   * The default is 30 seconds.
   */
  public OAuthCredentialsProviderBuilder proactiveTokenRefreshThreshold(
      final Duration proactiveTokenRefreshThreshold) {
    this.proactiveTokenRefreshThreshold = proactiveTokenRefreshThreshold;
    return this;
  }

  private OAuthCredentialsProviderBuilder proactiveTokenRefreshThreshold(
      final String proactiveTokenRefreshThreshold) {
    if (proactiveTokenRefreshThreshold != null) {
      return proactiveTokenRefreshThreshold(
          Duration.ofMillis(Long.parseLong(proactiveTokenRefreshThreshold)));
    }
    return this;
  }

  /**
   * @see #proactiveTokenRefreshThreshold(Duration)
   */
  public Duration getProactiveTokenRefreshThreshold() {
    return proactiveTokenRefreshThreshold;
  }

  /**
   * The maximum number of attempts (including the initial one) when fetching a token from the OAuth
   * authorization server. Retries are only attempted on transient failures (HTTP 404, 429, 5xx, or
   * IOException). The default value is {@value #DEFAULT_TOKEN_FETCH_MAX_RETRIES}.
   */
  public OAuthCredentialsProviderBuilder tokenFetchMaxRetries(final int tokenFetchMaxRetries) {
    this.tokenFetchMaxRetries = tokenFetchMaxRetries;
    return this;
  }

  private OAuthCredentialsProviderBuilder tokenFetchMaxRetries(final String tokenFetchMaxRetries) {
    if (tokenFetchMaxRetries != null) {
      return tokenFetchMaxRetries(Integer.parseInt(tokenFetchMaxRetries));
    }
    return this;
  }

  /**
   * @see #tokenFetchMaxRetries(int)
   */
  public int getTokenFetchMaxRetries() {
    return tokenFetchMaxRetries;
  }

  /**
   * The initial backoff duration applied between token fetch retry attempts. Subsequent delays grow
   * geometrically by {@link #tokenFetchBackoffMultiplier(double)}. The default value is 1 second.
   */
  public OAuthCredentialsProviderBuilder tokenFetchInitialBackoff(
      final Duration tokenFetchInitialBackoff) {
    this.tokenFetchInitialBackoff = tokenFetchInitialBackoff;
    return this;
  }

  private OAuthCredentialsProviderBuilder tokenFetchInitialBackoff(
      final String tokenFetchInitialBackoffMs) {
    if (tokenFetchInitialBackoffMs != null) {
      return tokenFetchInitialBackoff(
          Duration.ofMillis(Long.parseLong(tokenFetchInitialBackoffMs)));
    }
    return this;
  }

  /**
   * @see #tokenFetchInitialBackoff(Duration)
   */
  public Duration getTokenFetchInitialBackoff() {
    return tokenFetchInitialBackoff;
  }

  /**
   * The multiplier applied to the backoff duration between successive token fetch retry attempts.
   * Must be greater than or equal to 1.0. The default value is {@value
   * #DEFAULT_TOKEN_FETCH_BACKOFF_MULTIPLIER}.
   */
  public OAuthCredentialsProviderBuilder tokenFetchBackoffMultiplier(
      final double tokenFetchBackoffMultiplier) {
    this.tokenFetchBackoffMultiplier = tokenFetchBackoffMultiplier;
    return this;
  }

  private OAuthCredentialsProviderBuilder tokenFetchBackoffMultiplier(
      final String tokenFetchBackoffMultiplier) {
    if (tokenFetchBackoffMultiplier != null) {
      return tokenFetchBackoffMultiplier(Double.parseDouble(tokenFetchBackoffMultiplier));
    }
    return this;
  }

  /**
   * @see #tokenFetchBackoffMultiplier(double)
   */
  public double getTokenFetchBackoffMultiplier() {
    return tokenFetchBackoffMultiplier;
  }

  public OAuthCredentialsProviderBuilder clientAssertionKeystorePath(
      final String clientAssertionKeystorePath) {
    if (clientAssertionKeystorePath != null) {
      this.clientAssertionKeystorePath = Paths.get(clientAssertionKeystorePath);
    }
    return this;
  }

  public OAuthCredentialsProviderBuilder clientAssertionKeystorePath(
      final Path clientAssertionKeystorePath) {
    if (clientAssertionKeystorePath != null) {
      this.clientAssertionKeystorePath = clientAssertionKeystorePath;
    }
    return this;
  }

  public OAuthCredentialsProviderBuilder clientAssertionKeystorePassword(
      final String clientAssertionKeystorePassword) {
    this.clientAssertionKeystorePassword = clientAssertionKeystorePassword;
    return this;
  }

  public OAuthCredentialsProviderBuilder clientAssertionKeystoreKeyPassword(
      final String clientAssertionKeystoreKeyPassword) {
    this.clientAssertionKeystoreKeyPassword = clientAssertionKeystoreKeyPassword;
    return this;
  }

  public OAuthCredentialsProviderBuilder clientAssertionKeystoreKeyAlias(
      final String clientAssertionKeystoreKeyAlias) {
    this.clientAssertionKeystoreKeyAlias = clientAssertionKeystoreKeyAlias;
    return this;
  }

  public Path getClientAssertionKeystorePath() {
    return clientAssertionKeystorePath;
  }

  public String getClientAssertionKeystorePassword() {
    return clientAssertionKeystorePassword;
  }

  public String getClientAssertionKeystoreKeyAlias() {
    return clientAssertionKeystoreKeyAlias;
  }

  public String getClientAssertionKeystoreKeyPassword() {
    return clientAssertionKeystoreKeyPassword;
  }

  public boolean clientAssertionEnabled() {
    return clientAssertionKeystorePassword != null
        && !clientAssertionKeystorePassword.isEmpty()
        && clientAssertionKeystorePath != null
        && clientAssertionKeystorePath.toFile().exists();
  }

  public OAuthCredentialsProviderBuilder applyEnvironmentOverrides(
      final boolean applyEnvironmentOverrides) {
    this.applyEnvironmentOverrides = applyEnvironmentOverrides;
    return this;
  }

  /**
   * @return a new {@link OAuthCredentialsProvider} with the provided configuration options.
   */
  public OAuthCredentialsProvider build() {
    if (applyEnvironmentOverrides) {
      checkEnvironmentOverrides();
      applySSLClientCertConfiguration();
    }
    applyDefaults();
    findAuthorizationServerUrl();
    validate();
    return new OAuthCredentialsProvider(this);
  }

  private void findAuthorizationServerUrl() {
    final AuthorizationServerUrlSource source = detectSource();
    switch (source) {
      case unset:
        authorizationServerUrl = DEFAULT_AUTHZ_SERVER;
        break;
      case authorizationServerUrl:
        break;
      case wellKnownConfigurationUrl:
        authorizationServerUrlFromWellKnownConfigurationUrl();
        break;
      case issuerUrl:
        authorizationServerUrlFromIssuerUrl();
        break;
      default:
        throw new IllegalArgumentException(
            "Unsupported authorization server url source: " + source);
    }
  }

  private void authorizationServerUrlFromIssuerUrl() {
    if (issuerUrl.endsWith("/")) {
      issuerUrl = issuerUrl.substring(0, issuerUrl.length() - 1);
    }
    wellKnownConfigurationUrl = issuerUrl + "/.well-known/openid-configuration";
    authorizationServerUrlFromWellKnownConfigurationUrl();
  }

  private void authorizationServerUrlFromWellKnownConfigurationUrl() {
    try {
      final WellKnownConfiguration wellKnownConfiguration = fetchWellKnownConfiguration();
      authorizationServerUrl = wellKnownConfiguration.getTokenEndpoint();
    } catch (final IOException e) {
      throw new IllegalArgumentException("Failed to retrieve well known configuration", e);
    }
  }

  private WellKnownConfiguration fetchWellKnownConfiguration() throws IOException {
    final HttpURLConnection connection =
        (HttpURLConnection) new URL(wellKnownConfigurationUrl).openConnection();
    maybeConfigureCustomSSLContext(connection);
    connection.setRequestMethod("GET");
    connection.setRequestProperty("Accept", "application/json");
    connection.setDoOutput(true);
    connection.setReadTimeout(toIntExact(readTimeout.toMillis()));
    connection.setConnectTimeout(toIntExact(connectTimeout.toMillis()));
    connection.setRequestProperty("User-Agent", "camunda-client-java/" + VersionUtil.getVersion());

    if (connection.getResponseCode() != 200) {
      throw new IOException(
          String.format(
              "Failed while requesting well known configuration with status code %d and message %s.",
              connection.getResponseCode(), connection.getResponseMessage()));
    }

    try (final InputStream in = connection.getInputStream();
        final InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
      final ObjectReader wellKnownConfigurationReader =
          new ObjectMapper()
              .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
              .readerFor(WellKnownConfiguration.class);
      final WellKnownConfiguration wellKnownConfiguration =
          wellKnownConfigurationReader.readValue(reader);

      if (wellKnownConfiguration == null) {
        throw new IOException("Expected valid well known configuration but got null instead.");
      }

      return wellKnownConfiguration;
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

  private AuthorizationServerUrlSource detectSource() {
    if (isSet(authorizationServerUrl)) {
      return AuthorizationServerUrlSource.authorizationServerUrl;
    }
    if (isSet(wellKnownConfigurationUrl)) {
      return AuthorizationServerUrlSource.wellKnownConfigurationUrl;
    }
    if (isSet(issuerUrl)) {
      return AuthorizationServerUrlSource.issuerUrl;
    }
    return AuthorizationServerUrlSource.unset;
  }

  private boolean isSet(final String value) {
    return value != null && !value.isEmpty();
  }

  private void applySSLClientCertConfiguration() {
    applyEnvironmentValueIfNotNull(
        this::clientAssertionKeystorePath, OAUTH_ENV_CLIENT_ASSERTION_KEYSTORE_PATH);
    applyEnvironmentValueIfNotNull(
        this::clientAssertionKeystorePassword, OAUTH_ENV_CLIENT_ASSERTION_KEYSTORE_PASSWORD);
    applyEnvironmentValueIfNotNull(
        this::clientAssertionKeystoreKeyAlias, OAUTH_ENV_CLIENT_ASSERTION_KEYSTORE_KEY_ALIAS);
    applyEnvironmentValueIfNotNull(
        this::clientAssertionKeystoreKeyPassword, OAUTH_ENV_CLIENT_ASSERTION_KEYSTORE_KEY_PASSWORD);
  }

  private void checkEnvironmentOverrides() {
    applyEnvironmentValueIfNotNull(
        this::clientId,
        OAUTH_ENV_CLIENT_ID,
        LegacyZeebeClientEnvironmentVariables.OAUTH_ENV_CLIENT_ID);
    applyEnvironmentValueIfNotNull(
        this::clientSecret,
        OAUTH_ENV_CLIENT_SECRET,
        LegacyZeebeClientEnvironmentVariables.OAUTH_ENV_CLIENT_SECRET);
    applyEnvironmentValueIfNotNull(
        this::audience,
        OAUTH_ENV_TOKEN_AUDIENCE,
        LegacyZeebeClientEnvironmentVariables.OAUTH_ENV_TOKEN_AUDIENCE);
    applyEnvironmentValueIfNotNull(
        this::scope,
        OAUTH_ENV_TOKEN_SCOPE,
        LegacyZeebeClientEnvironmentVariables.OAUTH_ENV_TOKEN_SCOPE);
    applyEnvironmentValueIfNotNull(
        this::resource,
        OAUTH_ENV_TOKEN_RESOURCE,
        LegacyZeebeClientEnvironmentVariables.OAUTH_ENV_TOKEN_RESOURCE);
    applyEnvironmentValueIfNotNull(
        this::authorizationServerUrl,
        OAUTH_ENV_AUTHORIZATION_SERVER,
        LegacyZeebeClientEnvironmentVariables.OAUTH_ENV_AUTHORIZATION_SERVER);
    applyEnvironmentValueIfNotNull(
        this::wellKnownConfigurationUrl, OAUTH_ENV_WELL_KNOWN_CONFIGURATION_URL);
    applyEnvironmentValueIfNotNull(this::issuerUrl, OAUTH_ENV_ISSUER_URL);
    applyEnvironmentValueIfNotNull(
        this::keystorePath,
        OAUTH_ENV_SSL_CLIENT_KEYSTORE_PATH,
        LegacyZeebeClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_KEYSTORE_PATH);
    applyEnvironmentValueIfNotNull(
        this::keystorePassword,
        OAUTH_ENV_SSL_CLIENT_KEYSTORE_SECRET,
        LegacyZeebeClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_KEYSTORE_SECRET);
    applyEnvironmentValueIfNotNull(
        this::keystoreKeyPassword,
        OAUTH_ENV_SSL_CLIENT_KEYSTORE_KEY_SECRET,
        LegacyZeebeClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_KEYSTORE_KEY_SECRET);
    applyEnvironmentValueIfNotNull(
        this::truststorePath,
        OAUTH_ENV_SSL_CLIENT_TRUSTSTORE_PATH,
        LegacyZeebeClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_TRUSTSTORE_PATH);
    applyEnvironmentValueIfNotNull(
        this::truststorePassword,
        OAUTH_ENV_SSL_CLIENT_TRUSTSTORE_SECRET,
        LegacyZeebeClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_TRUSTSTORE_SECRET);
    applyEnvironmentValueIfNotNull(
        this::credentialsCachePath,
        OAUTH_ENV_CACHE_PATH,
        LegacyZeebeClientEnvironmentVariables.OAUTH_ENV_CACHE_PATH);
    applyEnvironmentValueIfNotNull(
        this::readTimeout,
        OAUTH_ENV_READ_TIMEOUT,
        LegacyZeebeClientEnvironmentVariables.OAUTH_ENV_READ_TIMEOUT);
    applyEnvironmentValueIfNotNull(
        this::connectTimeout,
        OAUTH_ENV_CONNECT_TIMEOUT,
        LegacyZeebeClientEnvironmentVariables.OAUTH_ENV_CONNECT_TIMEOUT);
    applyEnvironmentValueIfNotNull(
        this::proactiveTokenRefreshThreshold, OAUTH_ENV_PROACTIVE_TOKEN_REFRESH_THRESHOLD);
    applyEnvironmentValueIfNotNull(this::tokenFetchMaxRetries, OAUTH_ENV_TOKEN_FETCH_MAX_RETRIES);
    applyEnvironmentValueIfNotNull(
        this::tokenFetchInitialBackoff, OAUTH_ENV_TOKEN_FETCH_INITIAL_BACKOFF);
    applyEnvironmentValueIfNotNull(
        this::tokenFetchBackoffMultiplier, OAUTH_ENV_TOKEN_FETCH_BACKOFF_MULTIPLIER);
  }

  private void applyDefaults() {
    if (credentialsCachePath == null) {
      credentialsCachePath = DEFAULT_CREDENTIALS_CACHE_PATH;
    }

    if (connectTimeout == null) {
      connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    }

    if (readTimeout == null) {
      readTimeout = DEFAULT_READ_TIMEOUT;
    }
    if (proactiveTokenRefreshThreshold == null) {
      proactiveTokenRefreshThreshold = DEFAULT_PROACTIVE_TOKEN_REFRESH_THRESHOLD;
    }
    if (tokenFetchMaxRetries == null) {
      tokenFetchMaxRetries = DEFAULT_TOKEN_FETCH_MAX_RETRIES;
    }
    if (tokenFetchInitialBackoff == null) {
      tokenFetchInitialBackoff = DEFAULT_TOKEN_FETCH_INITIAL_BACKOFF;
    }
    if (tokenFetchBackoffMultiplier == null) {
      tokenFetchBackoffMultiplier = DEFAULT_TOKEN_FETCH_BACKOFF_MULTIPLIER;
    }
    if (clientAssertionKeystoreKeyPassword == null) {
      clientAssertionKeystoreKeyPassword = clientAssertionKeystorePassword;
    }
  }

  private void validate() {
    try {
      Objects.requireNonNull(clientId, String.format(INVALID_ARGUMENT_MSG, "client id"));
      if (clientAssertionEnabled()) {
        // loading the certificate from the provided path to ensure it exists and is valid
        final KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(
            Files.newInputStream(
                Paths.get(clientAssertionKeystorePath.toAbsolutePath().toString())),
            clientAssertionKeystorePassword.toCharArray());
        if (clientAssertionKeystoreKeyAlias == null) {
          // if the keystore key alias is not set, apply the first one
          clientAssertionKeystoreKeyAlias = keyStore.aliases().nextElement();
        }
      }
      Objects.requireNonNull(audience, String.format(INVALID_ARGUMENT_MSG, "audience"));
      Objects.requireNonNull(
          authorizationServerUrl, String.format(INVALID_ARGUMENT_MSG, "authorization server URL"));

      authorizationServer = new URL(authorizationServerUrl);

      if (keystorePath != null && !keystorePath.toFile().exists()) {
        throw new IllegalArgumentException("Keystore path does not exist: " + keystorePath);
      }

      if (truststorePath != null && !truststorePath.toFile().exists()) {
        throw new IllegalArgumentException("Truststore path does not exist: " + keystorePath);
      }

      credentialsCache = new File(credentialsCachePath);

      if (credentialsCache.isDirectory()) {
        throw new IllegalArgumentException(
            "Expected specified credentials cache to be a file but found directory instead.");
      }
      validateTimeout(connectTimeout, "ConnectTimeout");
      validateTimeout(readTimeout, "ReadTimeout");
      validateProactiveTokenRefreshThreshold(proactiveTokenRefreshThreshold);
      validateTokenFetchRetryConfig();
    } catch (final NullPointerException
        | IOException
        | KeyStoreException
        | NoSuchAlgorithmException
        | CertificateException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private void validateProactiveTokenRefreshThreshold(final Duration threshold) {
    if (threshold.isZero() || threshold.isNegative()) {
      throw new IllegalArgumentException(
          String.format(
              "Proactive token refresh threshold is %s milliseconds, expected a positive duration.",
              threshold.toMillis()));
    }
    if (threshold.compareTo(CamundaClientCredentials.EXPIRY_GRACE_PERIOD) <= 0) {
      throw new IllegalArgumentException(
          String.format(
              "Proactive token refresh threshold (%s ms) must be strictly larger than the expiry "
                  + "grace period (%s ms); otherwise eager refresh would never fire before the "
                  + "token is considered invalid.",
              threshold.toMillis(), CamundaClientCredentials.EXPIRY_GRACE_PERIOD.toMillis()));
    }
  }

  private void validateTokenFetchRetryConfig() {
    if (tokenFetchMaxRetries < 1) {
      throw new IllegalArgumentException(
          String.format(
              "tokenFetchMaxRetries is %d, expected a positive number greater than or equal to 1.",
              tokenFetchMaxRetries));
    }
    if (tokenFetchInitialBackoff.toMillis() < 1) {
      throw new IllegalArgumentException(
          String.format(
              "tokenFetchInitialBackoff is %s, expected a duration of at least 1 millisecond.",
              tokenFetchInitialBackoff));
    }
    if (tokenFetchBackoffMultiplier < 1.0) {
      throw new IllegalArgumentException(
          String.format(
              "tokenFetchBackoffMultiplier is %s, expected a value greater than or equal to 1.0.",
              tokenFetchBackoffMultiplier));
    }
  }

  private void validateTimeout(final Duration timeout, final String timeoutName) {
    if (timeout.isZero() || timeout.isNegative() || timeout.toMillis() > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          String.format(
              "%s timeout is %s milliseconds, expected timeout to be a positive number of milliseconds smaller than %s.",
              timeoutName, timeout.toMillis(), Integer.MAX_VALUE));
    }
  }

  static class WellKnownConfiguration {
    @JsonProperty("token_endpoint")
    private String tokenEndpoint;

    public String getTokenEndpoint() {
      return tokenEndpoint;
    }

    public void setTokenEndpoint(final String tokenEndpoint) {
      this.tokenEndpoint = tokenEndpoint;
    }
  }

  private enum AuthorizationServerUrlSource {
    authorizationServerUrl,
    wellKnownConfigurationUrl,
    issuerUrl,
    unset
  }
}
