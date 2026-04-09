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
package io.camunda.client.spring.properties;

import static io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder.*;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class CamundaClientAuthProperties {

  /**
   * The authentication method to use. If not set, it is detected based on the presence of a
   * username, password, client ID, and client secret. A default is set by <code>
   * camunda.client.mode: saas</code>.
   */
  private AuthMethod method;

  // basic auth
  /**
   * The username to use for basic authentication. A default is set by <code>
   * camunda.client.auth.method:
   * basic </code>.
   */
  private String username;

  /**
   * The password to be use for basic authentication. A default is set by <code>
   * camunda.client.auth.method: basic </code>.
   */
  private String password;

  // self-managed and saas
  /** The client ID to use when requesting an access token from the OAuth authorization server. */
  private String clientId;

  /**
   * The client secret to use when requesting an access token from the OAuth authorization server.
   */
  private String clientSecret;

  /**
   * The authorization server URL from which to request the access token. A default is set by <code>
   * camunda.client.mode: saas</code>.
   */
  private URI tokenUrl;

  /**
   * The url of the issuer for the access token. It is used to generate the well-known configuration
   * url from which the <code>token-url</code> is retrieved. Only applied if the <code>
   * camunda.client.auth.well-known-configuration-url</code> is not set. A default is set by <code>
   * camunda.client.auth.method: oidc</code>.
   */
  private URI issuerUrl;

  /**
   * The url of the well-known configuration of the issuer. It is used to retrieve the <code>
   * token-url</code>. Only applied if <code>camunda.client.auth.token-url</code> is not set.
   */
  private URI wellKnownConfigurationUrl;

  /**
   * The resource for which the access token must be valid. A default is set by <code>
   * camunda.client.mode: saas</code> and <code>camunda.client.auth.method: oidc</code>.
   */
  private String audience;

  /** The scopes of the access token. */
  private String scope;

  /** The resource for which the access token must be valid. */
  private String resource;

  /** The path to the keystore for the OAuth identity provider. */
  private Path keystorePath;

  /** The keystore password for the OAuth identity provider. */
  private String keystorePassword;

  /** The keystore key password for the OAuth identity provider. */
  private String keystoreKeyPassword;

  /** The path to the truststore for the OAuth identity provider. */
  private Path truststorePath;

  /** The truststore password for the OAuth identity provider. */
  private String truststorePassword;

  /** The path to the credentials cache file. */
  private String credentialsCachePath = DEFAULT_CREDENTIALS_CACHE_PATH;

  /** The connection timeout for requests to the OAuth credentials provider. */
  private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;

  /** The data read timeout for requests to the OAuth credentials provider. */
  private Duration readTimeout = DEFAULT_READ_TIMEOUT;

  /**
   * The lead time before actual token expiry at which a background refresh is triggered. The token
   * is still considered valid inside this window; this is a policy knob for how early refresh kicks
   * in so callers don't have to block on a synchronous refresh at the cliff edge. Must be strictly
   * larger than the internal expiry grace period.
   */
  private Duration proactiveTokenRefreshThreshold = DEFAULT_PROACTIVE_TOKEN_REFRESH_THRESHOLD;

  /**
   * The maximum number of attempts (including the initial one) when fetching a token from the OAuth
   * authorization server. Retries are only attempted on IOException or HTTP status codes configured
   * via {@code tokenFetchRetryableStatusCodes}.
   */
  private int tokenFetchMaxRetries = DEFAULT_TOKEN_FETCH_MAX_RETRIES;

  /**
   * The initial backoff duration applied between token fetch retry attempts. Subsequent delays grow
   * geometrically by {@link #tokenFetchBackoffMultiplier}.
   */
  private Duration tokenFetchInitialBackoff = DEFAULT_TOKEN_FETCH_INITIAL_BACKOFF;

  /**
   * The multiplier applied to the backoff duration between successive token fetch retry attempts.
   * Must be greater than or equal to 1.0.
   */
  private double tokenFetchBackoffMultiplier = DEFAULT_TOKEN_FETCH_BACKOFF_MULTIPLIER;

  /**
   * The set of HTTP status codes from the token endpoint that should be retried with backoff. Any
   * non-200 status code outside this set trips a non-retryable failure latch that fails fast for
   * the duration of tokenFetchNonRetryableCooldown.
   */
  private Set<Integer> tokenFetchRetryableStatusCodes = DEFAULT_TOKEN_FETCH_RETRYABLE_STATUS_CODES;

  /**
   * Duration for which token fetches fail fast after the token endpoint returns a non-retryable
   * response. After the cooldown elapses, the next call retries; if it fails again non-retryably,
   * the latch re-arms with a new cooldown. Set to Duration.ZERO to disable the cooldown entirely.
   */
  private Duration tokenFetchNonRetryableCooldown = DEFAULT_TOKEN_FETCH_NON_RETRYABLE_COOLDOWN;

  @NestedConfigurationProperty
  private CamundaClientAuthClientAssertionProperties clientAssertion =
      new CamundaClientAuthClientAssertionProperties();

  public CamundaClientAuthClientAssertionProperties getClientAssertion() {
    return clientAssertion;
  }

  public void setClientAssertion(final CamundaClientAuthClientAssertionProperties clientAssertion) {
    this.clientAssertion = clientAssertion;
  }

  public AuthMethod getMethod() {
    return method;
  }

  public void setMethod(final AuthMethod method) {
    this.method = method;
  }

  public URI getTokenUrl() {
    return tokenUrl;
  }

  public void setTokenUrl(final URI tokenUrl) {
    this.tokenUrl = tokenUrl;
  }

  public URI getIssuerUrl() {
    return issuerUrl;
  }

  public void setIssuerUrl(final URI issuerUrl) {
    this.issuerUrl = issuerUrl;
  }

  public URI getWellKnownConfigurationUrl() {
    return wellKnownConfigurationUrl;
  }

  public void setWellKnownConfigurationUrl(final URI wellKnownConfigurationUrl) {
    this.wellKnownConfigurationUrl = wellKnownConfigurationUrl;
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(final Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(final Duration readTimeout) {
    this.readTimeout = readTimeout;
  }

  public Duration getProactiveTokenRefreshThreshold() {
    return proactiveTokenRefreshThreshold;
  }

  public void setProactiveTokenRefreshThreshold(final Duration proactiveTokenRefreshThreshold) {
    this.proactiveTokenRefreshThreshold = proactiveTokenRefreshThreshold;
  }

  public int getTokenFetchMaxRetries() {
    return tokenFetchMaxRetries;
  }

  public void setTokenFetchMaxRetries(final int tokenFetchMaxRetries) {
    this.tokenFetchMaxRetries = tokenFetchMaxRetries;
  }

  public Duration getTokenFetchInitialBackoff() {
    return tokenFetchInitialBackoff;
  }

  public void setTokenFetchInitialBackoff(final Duration tokenFetchInitialBackoff) {
    this.tokenFetchInitialBackoff = tokenFetchInitialBackoff;
  }

  public double getTokenFetchBackoffMultiplier() {
    return tokenFetchBackoffMultiplier;
  }

  public void setTokenFetchBackoffMultiplier(final double tokenFetchBackoffMultiplier) {
    this.tokenFetchBackoffMultiplier = tokenFetchBackoffMultiplier;
  }

  public Set<Integer> getTokenFetchRetryableStatusCodes() {
    return tokenFetchRetryableStatusCodes;
  }

  public void setTokenFetchRetryableStatusCodes(final Set<Integer> tokenFetchRetryableStatusCodes) {
    this.tokenFetchRetryableStatusCodes = tokenFetchRetryableStatusCodes;
  }

  public Duration getTokenFetchNonRetryableCooldown() {
    return tokenFetchNonRetryableCooldown;
  }

  public void setTokenFetchNonRetryableCooldown(final Duration tokenFetchNonRetryableCooldown) {
    this.tokenFetchNonRetryableCooldown = tokenFetchNonRetryableCooldown;
  }

  public String getCredentialsCachePath() {
    return credentialsCachePath;
  }

  public void setCredentialsCachePath(final String credentialsCachePath) {
    this.credentialsCachePath = credentialsCachePath;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(final String clientId) {
    this.clientId = clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(final String clientSecret) {
    this.clientSecret = clientSecret;
  }

  public Path getKeystorePath() {
    return keystorePath;
  }

  public void setKeystorePath(final Path keystorePath) {
    this.keystorePath = keystorePath;
  }

  public String getKeystorePassword() {
    return keystorePassword;
  }

  public void setKeystorePassword(final String keystorePassword) {
    this.keystorePassword = keystorePassword;
  }

  public String getKeystoreKeyPassword() {
    return keystoreKeyPassword;
  }

  public void setKeystoreKeyPassword(final String keystoreKeyPassword) {
    this.keystoreKeyPassword = keystoreKeyPassword;
  }

  public Path getTruststorePath() {
    return truststorePath;
  }

  public void setTruststorePath(final Path truststorePath) {
    this.truststorePath = truststorePath;
  }

  public String getTruststorePassword() {
    return truststorePassword;
  }

  public void setTruststorePassword(final String truststorePassword) {
    this.truststorePassword = truststorePassword;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(final String audience) {
    this.audience = audience;
  }

  public String getResource() {
    return resource;
  }

  public void setResource(final String resource) {
    this.resource = resource;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(final String scope) {
    this.scope = scope;
  }

  @Override
  public String toString() {
    return "CamundaClientAuthProperties{"
        + "method="
        + method
        + ", username='"
        + username
        + '\''
        + ", password='"
        + (password != null ? "***" : null)
        + '\''
        + ", clientId='"
        + (clientId != null ? "***" : null)
        + '\''
        + ", clientSecret='"
        + (clientSecret != null ? "***" : null)
        + '\''
        + ", tokenUrl="
        + tokenUrl
        + ", issuerUrl="
        + issuerUrl
        + ", wellKnownConfigurationUrl="
        + wellKnownConfigurationUrl
        + ", audience='"
        + audience
        + '\''
        + ", scope='"
        + scope
        + '\''
        + ", resource='"
        + resource
        + '\''
        + ", keystorePath="
        + keystorePath
        + ", keystorePassword='"
        + (keystorePassword != null ? "***" : null)
        + '\''
        + ", keystoreKeyPassword='"
        + (keystoreKeyPassword != null ? "***" : null)
        + '\''
        + ", truststorePath="
        + truststorePath
        + ", truststorePassword='"
        + (truststorePassword != null ? "***" : null)
        + '\''
        + ", credentialsCachePath='"
        + credentialsCachePath
        + '\''
        + ", connectTimeout="
        + connectTimeout
        + ", readTimeout="
        + readTimeout
        + ", proactiveTokenRefreshThreshold="
        + proactiveTokenRefreshThreshold
        + ", tokenFetchMaxRetries="
        + tokenFetchMaxRetries
        + ", tokenFetchInitialBackoff="
        + tokenFetchInitialBackoff
        + ", tokenFetchBackoffMultiplier="
        + tokenFetchBackoffMultiplier
        + ", tokenFetchRetryableStatusCodes="
        + tokenFetchRetryableStatusCodes
        + ", tokenFetchNonRetryableCooldown="
        + tokenFetchNonRetryableCooldown
        + ", clientAssertion="
        + clientAssertion
        + '}';
  }

  public enum AuthMethod {
    none,
    oidc,
    basic
  }
}
