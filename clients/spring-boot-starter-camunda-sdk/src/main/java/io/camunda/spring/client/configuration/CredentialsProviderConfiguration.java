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
package io.camunda.spring.client.configuration;

import static java.util.Optional.ofNullable;

import io.camunda.client.CredentialsProvider;
import io.camunda.client.impl.NoopCredentialsProvider;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.spring.client.properties.CamundaClientProperties;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    final var clientMode = camundaClientProperties.getMode();

    return clientMode == null
        ? new NoopCredentialsProvider()
        : switch (clientMode) {
          case basic -> buildBasicAuthCredentialsProvider(camundaClientProperties);
          case saas, selfManaged -> buildOAuthCredentialsProvider(camundaClientProperties);
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
      LOG.warn("Failed to configure credential provider", e);
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
            .authorizationServerUrl(
                ofNullable(camundaClientProperties.getAuth().getTokenUrl())
                    .map(URI::toString)
                    .orElse(null))
            .credentialsCachePath(camundaClientProperties.getAuth().getCredentialsCachePath())
            .connectTimeout(camundaClientProperties.getAuth().getConnectTimeout())
            .readTimeout(camundaClientProperties.getAuth().getReadTimeout());

    maybeConfigureIdentityProviderSSLConfig(credBuilder, camundaClientProperties);
    try {
      return credBuilder.build();
    } catch (final Exception e) {
      LOG.warn("Failed to configure credential provider", e);
      return new NoopCredentialsProvider();
    }
  }

  private void maybeConfigureIdentityProviderSSLConfig(
      final OAuthCredentialsProviderBuilder builder,
      final CamundaClientProperties camundaClientProperties) {
    if (camundaClientProperties.getAuth().getKeystorePath() != null) {
      final Path keyStore = Paths.get(camundaClientProperties.getAuth().getKeystorePath());
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
      final Path trustStore = Paths.get(camundaClientProperties.getAuth().getTruststorePath());
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
