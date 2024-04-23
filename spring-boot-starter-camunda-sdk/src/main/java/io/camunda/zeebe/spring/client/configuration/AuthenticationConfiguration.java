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
package io.camunda.zeebe.spring.client.configuration;

import static java.util.Optional.*;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.identity.sdk.IdentityConfiguration.Type;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties.ClientMode;
import io.camunda.zeebe.spring.client.properties.common.ApiProperties;
import io.camunda.zeebe.spring.client.properties.common.AuthProperties;
import io.camunda.zeebe.spring.common.auth.Authentication;
import io.camunda.zeebe.spring.common.auth.DefaultNoopAuthentication;
import io.camunda.zeebe.spring.common.auth.Product;
import io.camunda.zeebe.spring.common.auth.identity.IdentityConfig;
import io.camunda.zeebe.spring.common.auth.identity.IdentityContainer;
import io.camunda.zeebe.spring.common.auth.jwt.JwtConfig;
import io.camunda.zeebe.spring.common.auth.jwt.JwtCredential;
import io.camunda.zeebe.spring.common.auth.saas.SaaSAuthentication;
import io.camunda.zeebe.spring.common.auth.selfmanaged.SelfManagedAuthentication;
import io.camunda.zeebe.spring.common.json.JsonMapper;
import java.net.URL;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableConfigurationProperties(CamundaClientProperties.class)
@ConditionalOnProperty(prefix = "camunda.client", name = "mode")
@Import(JsonMapperConfiguration.class)
public class AuthenticationConfiguration {
  private static final Logger LOG = LoggerFactory.getLogger(AuthenticationConfiguration.class);
  private final CamundaClientProperties camundaClientProperties;
  private final JsonMapper jsonMapper;

  @Autowired
  public AuthenticationConfiguration(
      final CamundaClientProperties camundaClientProperties, final JsonMapper jsonMapper) {
    this.camundaClientProperties = camundaClientProperties;
    this.jsonMapper = jsonMapper;
  }

  @Bean
  public Authentication camundaAuthentication() {
    final ClientMode clientMode = camundaClientProperties.getMode();
    // check which kind of authentication is used
    // oidc: configure zeebe
    if (ClientMode.oidc.equals(clientMode)) {
      final IdentityConfig identityConfig = new IdentityConfig();
      final JwtConfig jwtConfig = new JwtConfig();
      oidcCredentialForProduct(identityConfig, jwtConfig, Product.ZEEBE);

      return SelfManagedAuthentication.builder()
          .withJwtConfig(jwtConfig)
          .withIdentityConfig(identityConfig)
          .build();
    }

    if (ClientMode.saas.equals(clientMode)) {
      final JwtConfig jwtConfig = new JwtConfig();
      saasCredentialForProduct(jwtConfig, Product.ZEEBE);

      return SaaSAuthentication.builder()
          .withJwtConfig(jwtConfig)
          .withJsonMapper(jsonMapper)
          .build();
    }
    return new DefaultNoopAuthentication();
  }

  private void oidcCredentialForProduct(
      final IdentityConfig identityConfig, final JwtConfig jwtConfig, final Product product) {
    if (!enabledForProduct(product)) {
      LOG.debug("{} is disabled", product);
      return;
    }
    LOG.debug("{} is enabled", product);
    final String issuer = globalIssuer();
    final String clientId = clientId();
    final String clientSecret = clientSecret();
    final String audience = audienceForProduct(product);
    jwtConfig.addProduct(product, new JwtCredential(clientId, clientSecret, audience, issuer));
    final IdentityConfiguration identityCfg =
        new IdentityConfiguration(
            baseUrlForProduct(Product.IDENTITY).toString(),
            issuer,
            issuer,
            clientId,
            clientSecret,
            audience,
            globalOidcType().name());
    identityConfig.addProduct(
        product, new IdentityContainer(new Identity(identityCfg), identityCfg));
  }

  private void saasCredentialForProduct(final JwtConfig jwtConfig, final Product product) {
    if (!enabledForProduct(product)) {
      LOG.debug("{} is disabled", product);
      return;
    }
    LOG.debug("{} is enabled", product);
    final String issuer = globalIssuer();
    final String clientId = clientId();
    final String clientSecret = clientSecret();
    final String audience = audienceForProduct(product);
    jwtConfig.addProduct(product, new JwtCredential(clientId, clientSecret, audience, issuer));
  }

  private String globalIssuer() {
    return getGlobalAuthProperty("issuer", AuthProperties::getIssuer);
  }

  private Type globalOidcType() {
    return getGlobalAuthProperty("oidc type", AuthProperties::getOidcType);
  }

  private <T> T getGlobalAuthProperty(
      final String propertyName, final Function<AuthProperties, T> getter) {
    return ofNullable(camundaClientProperties.getAuth())
        .map(getter)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Could not detect required auth property " + propertyName));
  }

  private Boolean enabledForProduct(final Product product) {
    return getApiProperty("enabled", product, ApiProperties::getEnabled);
  }

  private URL baseUrlForProduct(final Product product) {
    return getApiProperty("base url", product, ApiProperties::getBaseUrl);
  }

  private String username() {
    return getAuthProperty("username", AuthProperties::getUsername);
  }

  private String password() {
    return getAuthProperty("password", AuthProperties::getPassword);
  }

  private String clientId() {
    return getAuthProperty("client id", AuthProperties::getClientId);
  }

  private String clientSecret() {
    return getAuthProperty("client secret", AuthProperties::getClientSecret);
  }

  private String audienceForProduct(final Product product) {
    return getApiProperty("audience", product, ApiProperties::getAudience);
  }

  private <T> T getApiProperty(
      final String propertyName, final Product product, final Function<ApiProperties, T> getter) {
    return getApiProperty(product + " " + propertyName, getter, apiPropertiesForProduct(product));
  }

  private <T> T getAuthProperty(
      final String propertyName, final Function<AuthProperties, T> getter) {
    return getAuthProperty(propertyName, getter, camundaClientProperties::getAuth);
  }

  private ApiPropertiesSupplier apiPropertiesForProduct(final Product product) {
    return apiPropertiesForProduct(camundaClientProperties, product);
  }

  private ApiPropertiesSupplier apiPropertiesForProduct(
      final CamundaClientProperties properties, final Product product) {
    switch (product) {
      case ZEEBE -> {
        return properties::getZeebe;
      }
      case IDENTITY -> {
        return properties::getIdentity;
      }
      default ->
          throw new IllegalStateException(
              "Could not detect auth properties supplier for product " + product);
    }
  }

  private <T> T getApiProperty(
      final String propertyName,
      final Function<ApiProperties, T> getter,
      final ApiPropertiesSupplier... alternatives) {
    for (final ApiPropertiesSupplier supplier : alternatives) {
      final ApiProperties properties = supplier.get();
      if (properties != null) {
        final T property = getter.apply(properties);
        if (property != null) {
          LOG.debug("Detected property {}", propertyName);
          return property;
        }
      }
    }
    throw new IllegalStateException("Could not detect required property " + propertyName);
  }

  private <T> T getAuthProperty(
      final String propertyName,
      final Function<AuthProperties, T> getter,
      final AuthPropertiesSupplier... alternatives) {
    for (final AuthPropertiesSupplier supplier : alternatives) {
      final AuthProperties properties = supplier.get();
      if (properties != null) {
        final T property = getter.apply(properties);
        if (property != null) {
          LOG.debug("Detected property {}", propertyName);
          return property;
        }
      }
    }
    throw new IllegalStateException("Could not detect required property " + propertyName);
  }

  private interface AuthPropertiesSupplier extends Supplier<AuthProperties> {}

  private interface ApiPropertiesSupplier extends Supplier<ApiProperties> {}
}
