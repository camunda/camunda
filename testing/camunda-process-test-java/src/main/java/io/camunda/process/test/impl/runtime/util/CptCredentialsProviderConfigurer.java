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
import io.camunda.process.test.impl.runtime.properties.RemoteRuntimeClientAuthProperties;
import io.camunda.process.test.impl.runtime.properties.RemoteRuntimeClientAuthProperties.AuthMethod;
import io.camunda.process.test.impl.runtime.properties.RemoteRuntimeClientProperties;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
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

  private static final Map<AuthMethod, Function<RemoteRuntimeClientProperties, CredentialsProvider>>
      PROVIDERS = new HashMap<>();

  static {
    PROVIDERS.put(
        AuthMethod.basic, CptCredentialsProviderConfigurer::buildBasicAuthCredentialsProvider);
    PROVIDERS.put(AuthMethod.oidc, CptCredentialsProviderConfigurer::buildOAuthCredentialsProvider);
    PROVIDERS.put(AuthMethod.none, cp -> new NoopCredentialsProvider());
  }

  public static CredentialsProvider configure(
      final RemoteRuntimeClientProperties clientProperties) {
    final AuthMethod authMethod = clientProperties.getAuthProperties().getMethod();

    return PROVIDERS
        .getOrDefault(authMethod, cp -> new NoopCredentialsProvider())
        .apply(clientProperties);
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

    return build(builder::build, "basic");
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
    return build(credBuilder::build, "oidc");
  }

  private static CredentialsProvider build(
      Supplier<CredentialsProvider> builder, String providerName) {
    try {
      return builder.get();
    } catch (final Exception e) {
      LOG.warn(
          "Failed to configure {} credential provider, falling back to no authentication. Cause: {}",
          providerName,
          e.getMessage());
      LOG.debug("Detailed error:", e);
      return new NoopCredentialsProvider();
    }
  }

  private static void maybeConfigureIdentityProviderSSLConfig(
      final OAuthCredentialsProviderBuilder builder,
      final RemoteRuntimeClientProperties clientProperties) {

    final RemoteRuntimeClientAuthProperties auth = clientProperties.getAuthProperties();

    configureStore(
        auth.getKeystorePath(),
        builder::keystorePath,
        builder::keystorePassword,
        auth.getKeystorePassword(),
        "keystore");
    configureStore(
        auth.getTruststorePath(),
        builder::truststorePath,
        builder::truststorePassword,
        auth.getTruststorePassword(),
        "truststore");

    if (auth.getKeystorePath() != null) {
      builder.keystoreKeyPassword(auth.getKeystoreKeyPassword());
    }
  }

  private static void configureStore(
      Path path,
      Consumer<Path> pathSetter,
      Consumer<String> passwordSetter,
      String password,
      String label) {

    if (path != null) {
      if (Files.exists(path)) {
        LOG.debug("Using {} {}", label, path);
        pathSetter.accept(path);
        passwordSetter.accept(password);
      } else {
        LOG.debug("{} {} not found", label, path);
      }
    }
  }
}
