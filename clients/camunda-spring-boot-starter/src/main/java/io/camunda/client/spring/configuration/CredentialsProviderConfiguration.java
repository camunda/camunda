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
package io.camunda.client.spring.configuration;

import static java.util.Optional.ofNullable;

import io.camunda.client.CredentialsProvider;
import io.camunda.client.impl.NoopCredentialsProvider;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.client.spring.properties.CamundaClientProperties;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CredentialsProviderConfiguration {
  private static final Logger LOG = LoggerFactory.getLogger(CredentialsProviderConfiguration.class);

  @Bean
  @ConditionalOnMissingBean
  public CredentialsProvider camundaClientCredentialsProvider(
      final CamundaClientProperties camundaClientProperties) {
    final var authMethod = camundaClientProperties.getAuth().getMethod();

    return authMethod == null
        ? new NoopCredentialsProvider()
        : switch (authMethod) {
          case basic -> buildBasicAuthCredentialsProvider(camundaClientProperties);
          case oidc -> buildOAuthCredentialsProvider(camundaClientProperties);
          case none -> new NoopCredentialsProvider();
        };
  }

  private CredentialsProvider buildBasicAuthCredentialsProvider(
      final CamundaClientProperties camundaClientProperties) {
    final var username = camundaClientProperties.getAuth().getUsername();
    final var password = camundaClientProperties.getAuth().getPassword();

    final var builder =
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

  private CredentialsProvider buildOAuthCredentialsProvider(
      final CamundaClientProperties camundaClientProperties) {
    final OAuthCredentialsProviderBuilder credBuilder =
        CredentialsProvider.newCredentialsProviderBuilder()
            .applyEnvironmentOverrides(false)
            .clientId(camundaClientProperties.getAuth().getClientId())
            .clientSecret(camundaClientProperties.getAuth().getClientSecret())
            .audience(camundaClientProperties.getAuth().getAudience())
            .scope(camundaClientProperties.getAuth().getScope())
            .resource(camundaClientProperties.getAuth().getResource())
            .authorizationServerUrl(fromURI(camundaClientProperties.getAuth().getTokenUrl()))
            .wellKnownConfigurationUrl(
                fromURI(camundaClientProperties.getAuth().getWellKnownConfigurationUrl()))
            .issuerUrl(fromURI(camundaClientProperties.getAuth().getIssuerUrl()))
            .credentialsCachePath(camundaClientProperties.getAuth().getCredentialsCachePath())
            .connectTimeout(camundaClientProperties.getAuth().getConnectTimeout())
            .readTimeout(camundaClientProperties.getAuth().getReadTimeout())
            .clientAssertionKeystorePath(
                camundaClientProperties.getAuth().getClientAssertion().getKeystorePath())
            .clientAssertionKeystorePassword(
                camundaClientProperties.getAuth().getClientAssertion().getKeystorePassword())
            .clientAssertionKeystoreKeyAlias(
                camundaClientProperties.getAuth().getClientAssertion().getKeystoreKeyAlias())
            .clientAssertionKeystoreKeyPassword(
                camundaClientProperties.getAuth().getClientAssertion().getKeystoreKeyPassword());

    maybeConfigureIdentityProviderSSLConfig(credBuilder, camundaClientProperties);
    return credBuilder.build();
  }

  private String fromURI(final URI uri) {
    return ofNullable(uri).map(URI::toString).orElse(null);
  }

  private void maybeConfigureIdentityProviderSSLConfig(
      final OAuthCredentialsProviderBuilder builder,
      final CamundaClientProperties camundaClientProperties) {
    if (camundaClientProperties.getAuth().getKeystorePath() != null) {
      final Path keyStore = camundaClientProperties.getAuth().getKeystorePath();
      if (Files.exists(keyStore)) {
        LOG.debug("Using keystore {}", keyStore);
        builder.keystorePath(keyStore);
        builder.keystorePassword(camundaClientProperties.getAuth().getKeystorePassword());
        builder.keystoreKeyPassword(camundaClientProperties.getAuth().getKeystoreKeyPassword());
      } else {
        LOG.debug("Keystore {} not found", keyStore);
      }
    }

    if (camundaClientProperties.getAuth().getTruststorePath() != null) {
      final Path trustStore = camundaClientProperties.getAuth().getTruststorePath();
      if (Files.exists(trustStore)) {
        LOG.debug("Using truststore {}", trustStore);
        builder.truststorePath(trustStore);
        builder.truststorePassword(camundaClientProperties.getAuth().getTruststorePassword());
      } else {
        LOG.debug("Truststore {} not found", trustStore);
      }
    }
  }
}
