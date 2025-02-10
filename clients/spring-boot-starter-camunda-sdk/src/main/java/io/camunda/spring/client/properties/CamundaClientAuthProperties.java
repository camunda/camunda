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

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

public class CamundaClientAuthProperties {

  // simple
  private String username;
  private String password;

  // self-managed and saas
  private String clientId;
  private String clientSecret;

  @Deprecated private URI issuer;
  private URI tokenUrl;
  private String audience;
  private String scope;

  private String keystorePath;
  private String keystorePassword;
  private String keystoreKeyPassword;
  private String truststorePath;
  private String truststorePassword;

  private String credentialsCachePath;
  private Duration connectTimeout;
  private Duration readTimeout;

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

  @Deprecated
  @DeprecatedConfigurationProperty(
      reason = "The expected property value if a token url, renamed",
      replacement = "camunda.client.auth.token-url")
  public URI getIssuer() {
    return issuer;
  }

  @Deprecated
  public void setIssuer(final URI issuer) {
    this.issuer = issuer;
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

  public String getKeystorePath() {
    return keystorePath;
  }

  public void setKeystorePath(final String keystorePath) {
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

  public String getTruststorePath() {
    return truststorePath;
  }

  public void setTruststorePath(final String truststorePath) {
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

  public String getScope() {
    return scope;
  }

  public void setScope(final String scope) {
    this.scope = scope;
  }

  @Override
  public String toString() {
    return "CamundaClientAuthProperties{"
        + "username='"
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
}
