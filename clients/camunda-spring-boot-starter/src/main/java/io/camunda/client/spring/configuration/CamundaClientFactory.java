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

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.impl.NoopCredentialsProvider;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.client.spring.properties.CamundaClientConfigurationProperties;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating {@link CamundaClient} instances from {@link
 * CamundaClientConfigurationProperties}.
 *
 * <p>This factory is used by the multi-client auto-configuration to create client instances for
 * each configured client.
 */
public class CamundaClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(CamundaClientFactory.class);

  /**
   * Creates a new {@link CamundaClient} from the given configuration properties.
   *
   * @param name the name of the client (used for logging)
   * @param properties the configuration properties
   * @return a configured CamundaClient instance
   */
  public CamundaClient createClient(
      final String name, final CamundaClientConfigurationProperties properties) {
    LOG.debug("Creating CamundaClient '{}' with properties: {}", name, properties);

    final CredentialsProvider credentialsProvider = buildCredentialsProvider(properties);

    final CamundaClientBuilder builder =
        CamundaClient.newClientBuilder()
            .restAddress(properties.getRestAddress())
            .grpcAddress(properties.getGrpcAddress())
            .defaultTenantId(properties.getTenantId())
            .defaultRequestTimeout(properties.getRequestTimeout())
            .defaultJobTimeout(properties.getWorker().getDefaults().getTimeout())
            .defaultJobWorkerMaxJobsActive(properties.getWorker().getDefaults().getMaxJobsActive())
            .defaultJobWorkerName(properties.getWorker().getDefaults().getName())
            .defaultJobPollInterval(properties.getWorker().getDefaults().getPollInterval())
            .defaultJobWorkerStreamEnabled(properties.getWorker().getDefaults().getStreamEnabled())
            .defaultMessageTimeToLive(properties.getMessageTimeToLive())
            .numJobWorkerExecutionThreads(properties.getExecutionThreads())
            .credentialsProvider(credentialsProvider)
            .keepAlive(properties.getKeepAlive())
            .maxMessageSize(Math.toIntExact(properties.getMaxMessageSize().toBytes()))
            .maxMetadataSize(Math.toIntExact(properties.getMaxMetadataSize().toBytes()))
            .preferRestOverGrpc(properties.getPreferRestOverGrpc());

    if (properties.getCaCertificatePath() != null) {
      builder.caCertificatePath(properties.getCaCertificatePath());
    }

    if (properties.getOverrideAuthority() != null) {
      builder.overrideAuthority(properties.getOverrideAuthority());
    }

    final var tenantIds = properties.getWorker().getDefaults().getTenantIds();
    if (tenantIds != null && !tenantIds.isEmpty()) {
      builder.defaultJobWorkerTenantIds(tenantIds);
    }

    return builder.build();
  }

  private CredentialsProvider buildCredentialsProvider(
      final CamundaClientConfigurationProperties properties) {
    final var authMethod = properties.getAuth().getMethod();

    if (authMethod == null) {
      return new NoopCredentialsProvider();
    }

    return switch (authMethod) {
      case basic -> buildBasicAuthCredentialsProvider(properties);
      case oidc -> buildOAuthCredentialsProvider(properties);
      case none -> new NoopCredentialsProvider();
    };
  }

  private CredentialsProvider buildBasicAuthCredentialsProvider(
      final CamundaClientConfigurationProperties properties) {
    final var username = properties.getAuth().getUsername();
    final var password = properties.getAuth().getPassword();

    final var builder =
        new BasicAuthCredentialsProviderBuilder()
            .applyEnvironmentOverrides(false)
            .username(username)
            .password(password);

    try {
      return builder.build();
    } catch (final Exception e) {
      LOG.warn(
          "Failed to configure basic credential provider, falling back to no authentication: {}",
          e.getMessage());
      if (LOG.isDebugEnabled()) {
        LOG.debug(e.getMessage(), e);
      }
      return new NoopCredentialsProvider();
    }
  }

  private CredentialsProvider buildOAuthCredentialsProvider(
      final CamundaClientConfigurationProperties properties) {
    final OAuthCredentialsProviderBuilder credBuilder =
        CredentialsProvider.newCredentialsProviderBuilder()
            .applyEnvironmentOverrides(false)
            .clientId(properties.getAuth().getClientId())
            .clientSecret(properties.getAuth().getClientSecret())
            .audience(properties.getAuth().getAudience())
            .scope(properties.getAuth().getScope())
            .resource(properties.getAuth().getResource())
            .authorizationServerUrl(fromURI(properties.getAuth().getTokenUrl()))
            .wellKnownConfigurationUrl(fromURI(properties.getAuth().getWellKnownConfigurationUrl()))
            .issuerUrl(fromURI(properties.getAuth().getIssuerUrl()))
            .credentialsCachePath(properties.getAuth().getCredentialsCachePath())
            .connectTimeout(properties.getAuth().getConnectTimeout())
            .readTimeout(properties.getAuth().getReadTimeout())
            .clientAssertionKeystorePath(
                properties.getAuth().getClientAssertion().getKeystorePath())
            .clientAssertionKeystorePassword(
                properties.getAuth().getClientAssertion().getKeystorePassword())
            .clientAssertionKeystoreKeyAlias(
                properties.getAuth().getClientAssertion().getKeystoreKeyAlias())
            .clientAssertionKeystoreKeyPassword(
                properties.getAuth().getClientAssertion().getKeystoreKeyPassword());

    maybeConfigureIdentityProviderSSLConfig(credBuilder, properties);
    return credBuilder.build();
  }

  private String fromURI(final URI uri) {
    return ofNullable(uri).map(URI::toString).orElse(null);
  }

  private void maybeConfigureIdentityProviderSSLConfig(
      final OAuthCredentialsProviderBuilder builder,
      final CamundaClientConfigurationProperties properties) {
    if (properties.getAuth().getKeystorePath() != null) {
      final Path keyStore = properties.getAuth().getKeystorePath();
      if (Files.exists(keyStore)) {
        LOG.debug("Using keystore {}", keyStore);
        builder.keystorePath(keyStore);
        builder.keystorePassword(properties.getAuth().getKeystorePassword());
        builder.keystoreKeyPassword(properties.getAuth().getKeystoreKeyPassword());
      } else {
        LOG.debug("Keystore {} not found", keyStore);
      }
    }

    if (properties.getAuth().getTruststorePath() != null) {
      final Path trustStore = properties.getAuth().getTruststorePath();
      if (Files.exists(trustStore)) {
        LOG.debug("Using truststore {}", trustStore);
        builder.truststorePath(trustStore);
        builder.truststorePassword(properties.getAuth().getTruststorePassword());
      } else {
        LOG.debug("Truststore {} not found", trustStore);
      }
    }
  }
}
