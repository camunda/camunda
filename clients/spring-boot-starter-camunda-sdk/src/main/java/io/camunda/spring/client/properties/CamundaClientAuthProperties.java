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
package io.camunda.spring.client.properties;

import static io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder.*;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class CamundaClientAuthProperties {

  /**
   * Authentication method to be used. If not set, it will be detected based on the presence of
   * username, password, client id and client secret. A default is set by `camunda.client.mode:
   * saas`.
   */
  private AuthMethod method;

  // basic auth
  /**
   * Username to be used for basic authentication. A default is set by `camunda.client.auth.method:
   * basic`.
   */
  private String username;

  /**
   * Password to be used for basic authentication. A default is set by `camunda.client.auth.method:
   * basic`.
   */
  private String password;

  // self-managed and saas
  /** Client id to be used when requesting access token from OAuth authorization server. */
  private String clientId;

  /** Client secret to be used when requesting access token from OAuth authorization server. */
  private String clientSecret;

  /**
   * The authorization server's URL, from which the access token will be requested. A default is set
   * by `camunda.client.mode: saas` and `camunda.client.auth.method: oidc`.
   */
  private URI tokenUrl;

  /**
   * The resource for which the access token should be valid. A default is set by
   * `camunda.client.mode: saas` and `camunda.client.auth.method: oidc`.
   */
  private String audience;

  /** The scopes of the access token. */
  private String scope;

  /** The resource for which the access token should be valid. */
  private String resource;

  /** Path to keystore used for OAuth identity provider. */
  private Path keystorePath;

  /** Password to keystore used for OAuth identity provider. */
  private String keystorePassword;

  /** Keystore key password used for OAuth identity provider. */
  private String keystoreKeyPassword;

  /** Path to truststore used for OAuth identity provider. */
  private Path truststorePath;

  /** Password to truststore used for OAuth identity provider. */
  private String truststorePassword;

  /** The location for the credentials cache file. */
  private String credentialsCachePath = DEFAULT_CREDENTIALS_CACHE_PATH;

  /** The connection timeout of requests to the OAuth credentials provider. */
  private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;

  /** The data read timeout of requests to the OAuth credentials provider. */
  private Duration readTimeout = DEFAULT_READ_TIMEOUT;

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
        + "method='"
        + method
        + '\''
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
        + ", audience='"
        + audience
        + '\''
        + ", scope='"
        + scope
        + '\''
        + ", resource='"
        + resource
        + '\''
        + ", keystorePath='"
        + keystorePath
        + '\''
        + ", keystorePassword='"
        + (keystorePassword != null ? "***" : null)
        + '\''
        + ", keystoreKeyPassword='"
        + (keystoreKeyPassword != null ? "***" : null)
        + '\''
        + ", truststorePath='"
        + truststorePath
        + '\''
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
        + '}';
  }

  public enum AuthMethod {
    none,
    oidc,
    basic
  }
}
