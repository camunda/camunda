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
package io.camunda.process.test.impl.runtime.properties;

import static io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder.DEFAULT_CONNECT_TIMEOUT;
import static io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder.DEFAULT_CREDENTIALS_CACHE_PATH;
import static io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder.DEFAULT_READ_TIMEOUT;
import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrDefault;
import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrNull;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Properties;

public class RemoteRuntimeClientAuthProperties {
  public static final String PROPERTY_NAME_METHOD = "remote.client.auth.method";
  public static final String PROPERTY_NAME_USERNAME = "remote.client.auth.username";
  public static final String PROPERTY_NAME_PASSWORD = "remote.client.auth.password";
  public static final String PROPERTY_NAME_CLIENT_ID = "remote.client.auth.clientId";
  public static final String PROPERTY_NAME_CLIENT_SECRET = "remote.client.auth.clientSecret";
  public static final String PROPERTY_NAME_TOKEN_URL = "remote.client.auth.tokenUrl";
  public static final String PROPERTY_NAME_AUDIENCE = "remote.client.auth.audience";
  public static final String PROPERTY_NAME_SCOPE = "remote.client.auth.scope";
  public static final String PROPERTY_NAME_RESOURCE = "remote.client.auth.resource";
  public static final String PROPERTY_NAME_KEYSTORE_PATH = "remote.client.auth.keystorePath";
  public static final String PROPERTY_NAME_KEYSTORE_PASSWORD =
      "remote.client.auth.keystorePassword";
  public static final String PROPERTY_NAME_KEYSTORE_KEY_PASSWORD =
      "remote.client.auth.keystoreKeyPassword";
  public static final String PROPERTY_NAME_TRUSTSTORE_PATH = "remote.client.auth.truststorePath";
  public static final String PROPERTY_NAME_TRUSTSTORE_PASSWORD =
      "remote.client.auth.truststorePassword";
  public static final String PROPERTY_NAME_CREDENTIALS_CACHE_PATH =
      "remote.client.auth.credentialsCachePath";
  public static final String PROPERTY_NAME_CONNECT_TIMEOUT = "remote.client.auth.connectTimeout";
  public static final String PROPERTY_NAME_READ_TIMEOUT = "remote.client.auth.readTimeout";

  public static final String PROPERTY_NAME_CLIENT_ASSERTION_KEYSTORE_PATH =
      "remote.client.auth.clientAssertion.keystorePath";
  public static final String PROPERTY_NAME_CLIENT_ASSERTION_KEYSTORE_PASSWORD =
      "remote.client.auth.clientAssertion.keystorePassword";
  public static final String PROPERTY_NAME_CLIENT_ASSERTION_KEYSTORE_KEY_ALIAS =
      "remote.client.auth.clientAssertion.keystoreKeyAlias";
  public static final String PROPERTY_NAME_CLIENT_ASSERTION_KEYSTORE_KEY_PASSWORD =
      "remote.client.auth.clientAssertion.keystoreKeyPassword";

  private final AuthMethod method;

  // basic auth
  private final String username;
  private final String password;

  // self-managed and saas
  private final String clientId;
  private final String clientSecret;
  private final URI tokenUrl;
  private final String audience;
  private final String scope;
  private final String resource;
  private final Path keystorePath;
  private final String keystorePassword;
  private final String keystoreKeyPassword;
  private final Path truststorePath;
  private final String truststorePassword;
  private final String credentialsCachePath;

  private final Duration connectTimeout;
  private final Duration readTimeout;

  // client assertion
  private final Path clientAssertionKeystorePath;
  private final String clientAssertionKeystorePassword;
  private final String clientAssertionKeystoreKeyAlias;
  private final String clientAssertionKeystoreKeyPassword;

  public RemoteRuntimeClientAuthProperties(final Properties properties) {
    method =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_METHOD,
            v -> AuthMethod.valueOf(v.toLowerCase()),
            AuthMethod.none);

    username = getPropertyOrNull(properties, PROPERTY_NAME_USERNAME);

    password = getPropertyOrNull(properties, PROPERTY_NAME_PASSWORD);

    clientId = getPropertyOrNull(properties, PROPERTY_NAME_CLIENT_ID);

    clientSecret = getPropertyOrNull(properties, PROPERTY_NAME_CLIENT_SECRET);

    tokenUrl = getPropertyOrNull(properties, PROPERTY_NAME_TOKEN_URL, URI::create);

    audience = getPropertyOrNull(properties, PROPERTY_NAME_AUDIENCE);

    scope = getPropertyOrNull(properties, PROPERTY_NAME_SCOPE);

    resource = getPropertyOrNull(properties, PROPERTY_NAME_RESOURCE);

    keystorePath = getPropertyOrNull(properties, PROPERTY_NAME_KEYSTORE_PATH, Paths::get);

    keystorePassword = getPropertyOrNull(properties, PROPERTY_NAME_KEYSTORE_PASSWORD);

    keystoreKeyPassword = getPropertyOrNull(properties, PROPERTY_NAME_KEYSTORE_KEY_PASSWORD);

    truststorePath = getPropertyOrNull(properties, PROPERTY_NAME_TRUSTSTORE_PATH, Paths::get);

    truststorePassword = getPropertyOrNull(properties, PROPERTY_NAME_TRUSTSTORE_PASSWORD);

    credentialsCachePath =
        getPropertyOrDefault(
            properties, PROPERTY_NAME_CREDENTIALS_CACHE_PATH, DEFAULT_CREDENTIALS_CACHE_PATH);

    connectTimeout =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_CONNECT_TIMEOUT,
            v -> {
              try {
                return Duration.parse(v);
              } catch (final Throwable t) {
                return DEFAULT_CONNECT_TIMEOUT;
              }
            },
            DEFAULT_CONNECT_TIMEOUT);

    readTimeout =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_READ_TIMEOUT,
            v -> {
              try {
                return Duration.parse(v);
              } catch (final Throwable t) {
                return DEFAULT_READ_TIMEOUT;
              }
            },
            DEFAULT_READ_TIMEOUT);

    clientAssertionKeystorePath =
        getPropertyOrNull(properties, PROPERTY_NAME_CLIENT_ASSERTION_KEYSTORE_PATH, Paths::get);

    clientAssertionKeystorePassword =
        getPropertyOrNull(properties, PROPERTY_NAME_CLIENT_ASSERTION_KEYSTORE_PASSWORD);

    clientAssertionKeystoreKeyAlias =
        getPropertyOrNull(properties, PROPERTY_NAME_CLIENT_ASSERTION_KEYSTORE_KEY_ALIAS);

    clientAssertionKeystoreKeyPassword =
        getPropertyOrNull(properties, PROPERTY_NAME_CLIENT_ASSERTION_KEYSTORE_KEY_PASSWORD);
  }

  public AuthMethod getMethod() {
    return method;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public String getClientId() {
    return clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public URI getTokenUrl() {
    return tokenUrl;
  }

  public String getAudience() {
    return audience;
  }

  public String getScope() {
    return scope;
  }

  public String getResource() {
    return resource;
  }

  public Path getKeystorePath() {
    return keystorePath;
  }

  public String getKeystorePassword() {
    return keystorePassword;
  }

  public String getKeystoreKeyPassword() {
    return keystoreKeyPassword;
  }

  public Path getTruststorePath() {
    return truststorePath;
  }

  public String getTruststorePassword() {
    return truststorePassword;
  }

  public String getCredentialsCachePath() {
    return credentialsCachePath;
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public Duration getReadTimeout() {
    return readTimeout;
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

  public enum AuthMethod {
    none,
    oidc,
    basic
  }
}
