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
package io.camunda.process.test.impl.runtime.util;

import static java.util.Optional.ofNullable;

import io.camunda.client.CredentialsProvider;
import io.camunda.client.impl.NoopCredentialsProvider;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.process.test.impl.runtime.properties.RemoteRuntimeClientAuthProperties.AuthMethod;
import io.camunda.process.test.impl.runtime.properties.RemoteRuntimeClientProperties;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * This is a near duplicate of the CredentialsProviderConfiguration found in the
 * spring-boot-starter-camunda-sdk project. However, because that implementation is heavily
 * reliant on spring boot's autoconfiguration, the decision was made to copy the implementation
 * for the CPT context.
 */
/** Configures the CredentialsProvider for a CamundaCloudClient. */
public class CptCredentialsProviderConfigurer {
  private static final Logger LOG = LoggerFactory.getLogger(CptCredentialsProviderConfigurer.class);

  public static CredentialsProvider configure(
      final RemoteRuntimeClientProperties clientProperties) {
    final AuthMethod authMethod = clientProperties.getAuthProperties().getMethod();

    if (authMethod == null) {
      return new NoopCredentialsProvider();
    }

    switch (authMethod) {
      case basic:
        return buildBasicAuthCredentialsProvider(clientProperties);
      case oidc:
        return buildOAuthCredentialsProvider(clientProperties);
      default:
        return new NoopCredentialsProvider();
    }
  }

  private static CredentialsProvider buildBasicAuthCredentialsProvider(
      final RemoteRuntimeClientProperties clientProperties) {
    final String username = clientProperties.getAuthProperties().getUsername();
    final String password = clientProperties.getAuthProperties().getPassword();

    final BasicAuthCredentialsProviderBuilder builder =
        new BasicAuthCredentialsProviderBuilder()
            .applyEnvironmentOverrides(false)
            .username(username)
            .password(password);

    try {
      return builder.build();
    } catch (final Exception e) {
      LOG.warn(
          "Failed to configure basic credential provider, falling back to use no authentication, cause: {}",
          e.getMessage());
      if (LOG.isDebugEnabled()) {
        LOG.debug(e.getMessage(), e);
      }
      return new NoopCredentialsProvider();
    }
  }

  private static CredentialsProvider buildOAuthCredentialsProvider(
      final RemoteRuntimeClientProperties clientProperties) {

    final OAuthCredentialsProviderBuilder credBuilder =
        CredentialsProvider.newCredentialsProviderBuilder()
            .applyEnvironmentOverrides(false)
            .clientId(clientProperties.getAuthProperties().getClientId())
            .clientSecret(clientProperties.getAuthProperties().getClientSecret())
            .audience(clientProperties.getAuthProperties().getAudience())
            .scope(clientProperties.getAuthProperties().getScope())
            .resource(clientProperties.getAuthProperties().getResource())
            .authorizationServerUrl(
                ofNullable(clientProperties.getAuthProperties().getTokenUrl())
                    .map(URI::toString)
                    .orElse(null))
            .credentialsCachePath(clientProperties.getAuthProperties().getCredentialsCachePath())
            .connectTimeout(clientProperties.getAuthProperties().getConnectTimeout())
            .readTimeout(clientProperties.getAuthProperties().getReadTimeout())
            .clientAssertionKeystorePath(
                clientProperties.getAuthProperties().getClientAssertionKeystorePath())
            .clientAssertionKeystorePassword(
                clientProperties.getAuthProperties().getClientAssertionKeystorePassword())
            .clientAssertionKeystoreKeyAlias(
                clientProperties.getAuthProperties().getClientAssertionKeystoreKeyAlias())
            .clientAssertionKeystoreKeyPassword(
                clientProperties.getAuthProperties().getClientAssertionKeystoreKeyPassword());

    maybeConfigureIdentityProviderSSLConfig(credBuilder, clientProperties);
    try {
      return credBuilder.build();
    } catch (final Exception e) {
      LOG.warn(
          "Failed to configure oidc credential provider, falling back to use no authentication, cause: {}",
          e.getMessage());
      if (LOG.isDebugEnabled()) {
        LOG.debug(e.getMessage(), e);
      }
      return new NoopCredentialsProvider();
    }
  }

  private static void maybeConfigureIdentityProviderSSLConfig(
      final OAuthCredentialsProviderBuilder builder,
      final RemoteRuntimeClientProperties clientProperties) {
    if (clientProperties.getAuthProperties().getKeystorePath() != null) {
      final Path keyStore = clientProperties.getAuthProperties().getKeystorePath();
      if (Files.exists(keyStore)) {
        LOG.debug("Using keystore {}", keyStore);
        builder.keystorePath(keyStore);
        builder.keystorePassword(clientProperties.getAuthProperties().getKeystorePassword());
        builder.keystoreKeyPassword(clientProperties.getAuthProperties().getKeystoreKeyPassword());
      } else {
        LOG.debug("Keystore {} not found", keyStore);
      }
    }

    if (clientProperties.getAuthProperties().getTruststorePath() != null) {
      final Path trustStore = clientProperties.getAuthProperties().getTruststorePath();
      if (Files.exists(trustStore)) {
        LOG.debug("Using truststore {}", trustStore);
        builder.truststorePath(trustStore);
        builder.truststorePassword(clientProperties.getAuthProperties().getTruststorePassword());
      } else {
        LOG.debug("Truststore {} not found", trustStore);
      }
    }
  }
}
