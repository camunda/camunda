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
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_READ_TIMEOUT;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_KEYSTORE_KEY_SECRET;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_KEYSTORE_PATH;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_KEYSTORE_SECRET;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_TRUSTSTORE_PATH;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_TRUSTSTORE_SECRET;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_TOKEN_AUDIENCE;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_TOKEN_RESOURCE;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_TOKEN_SCOPE;

import io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.Objects;

public final class OAuthCredentialsProviderBuilder {
  public static final String INVALID_ARGUMENT_MSG = "Expected valid %s but none was provided.";
  public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
  public static final Duration DEFAULT_READ_TIMEOUT = DEFAULT_CONNECT_TIMEOUT;
  public static final String DEFAULT_CREDENTIALS_CACHE_PATH =
      Paths.get(System.getProperty("user.home"), ".camunda", "credentials")
          .toAbsolutePath()
          .toString();
  private static final String DEFAULT_AUTHZ_SERVER = "https://login.cloud.camunda.io/oauth/token/";
  private String clientId;
  private String clientSecret;
  private String audience;
  private String scope;
  private String resource;
  private String authorizationServerUrl;
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

  /**
   * @see OAuthCredentialsProviderBuilder#authorizationServerUrl(String)
   */
  URL getAuthorizationServer() {
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

    validate();
    return new OAuthCredentialsProvider(this);
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
        this::clientId, OAUTH_ENV_CLIENT_ID, ZeebeClientEnvironmentVariables.OAUTH_ENV_CLIENT_ID);
    applyEnvironmentValueIfNotNull(
        this::clientSecret,
        OAUTH_ENV_CLIENT_SECRET,
        ZeebeClientEnvironmentVariables.OAUTH_ENV_CLIENT_SECRET);
    applyEnvironmentValueIfNotNull(
        this::audience,
        OAUTH_ENV_TOKEN_AUDIENCE,
        ZeebeClientEnvironmentVariables.OAUTH_ENV_TOKEN_AUDIENCE);
    applyEnvironmentValueIfNotNull(
        this::scope, OAUTH_ENV_TOKEN_SCOPE, ZeebeClientEnvironmentVariables.OAUTH_ENV_TOKEN_SCOPE);
    applyEnvironmentValueIfNotNull(
        this::resource,
        OAUTH_ENV_TOKEN_RESOURCE,
        ZeebeClientEnvironmentVariables.OAUTH_ENV_TOKEN_RESOURCE);
    applyEnvironmentValueIfNotNull(
        this::authorizationServerUrl,
        OAUTH_ENV_AUTHORIZATION_SERVER,
        ZeebeClientEnvironmentVariables.OAUTH_ENV_AUTHORIZATION_SERVER);
    applyEnvironmentValueIfNotNull(
        this::keystorePath,
        OAUTH_ENV_SSL_CLIENT_KEYSTORE_PATH,
        ZeebeClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_KEYSTORE_PATH);
    applyEnvironmentValueIfNotNull(
        this::keystorePassword,
        OAUTH_ENV_SSL_CLIENT_KEYSTORE_SECRET,
        ZeebeClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_KEYSTORE_SECRET);
    applyEnvironmentValueIfNotNull(
        this::keystoreKeyPassword,
        OAUTH_ENV_SSL_CLIENT_KEYSTORE_KEY_SECRET,
        ZeebeClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_KEYSTORE_KEY_SECRET);
    applyEnvironmentValueIfNotNull(
        this::truststorePath,
        OAUTH_ENV_SSL_CLIENT_TRUSTSTORE_PATH,
        ZeebeClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_TRUSTSTORE_PATH);
    applyEnvironmentValueIfNotNull(
        this::truststorePassword,
        OAUTH_ENV_SSL_CLIENT_TRUSTSTORE_SECRET,
        ZeebeClientEnvironmentVariables.OAUTH_ENV_SSL_CLIENT_TRUSTSTORE_SECRET);
    applyEnvironmentValueIfNotNull(
        this::credentialsCachePath,
        OAUTH_ENV_CACHE_PATH,
        ZeebeClientEnvironmentVariables.OAUTH_ENV_CACHE_PATH);
    applyEnvironmentValueIfNotNull(
        this::readTimeout,
        OAUTH_ENV_READ_TIMEOUT,
        ZeebeClientEnvironmentVariables.OAUTH_ENV_READ_TIMEOUT);
    applyEnvironmentValueIfNotNull(
        this::connectTimeout,
        OAUTH_ENV_CONNECT_TIMEOUT,
        ZeebeClientEnvironmentVariables.OAUTH_ENV_CONNECT_TIMEOUT);
  }

  private void applyDefaults() {
    if (credentialsCachePath == null) {
      credentialsCachePath = DEFAULT_CREDENTIALS_CACHE_PATH;
    }

    if (authorizationServerUrl == null) {
      authorizationServerUrl = DEFAULT_AUTHZ_SERVER;
    }

    if (connectTimeout == null) {
      connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    }

    if (readTimeout == null) {
      readTimeout = DEFAULT_READ_TIMEOUT;
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
      } else {
        Objects.requireNonNull(clientSecret, String.format(INVALID_ARGUMENT_MSG, "client secret"));
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
    } catch (final NullPointerException
        | IOException
        | KeyStoreException
        | NoSuchAlgorithmException
        | CertificateException e) {
      throw new IllegalArgumentException(e);
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
}
