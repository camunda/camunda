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

import static org.springframework.util.StringUtils.hasText;

import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.zeebe.spring.client.properties.*;
import io.camunda.zeebe.spring.common.auth.Authentication;
import io.camunda.zeebe.spring.common.auth.DefaultNoopAuthentication;
import io.camunda.zeebe.spring.common.auth.Product;
import io.camunda.zeebe.spring.common.auth.identity.IdentityConfig;
import io.camunda.zeebe.spring.common.auth.jwt.JwtConfig;
import io.camunda.zeebe.spring.common.auth.jwt.JwtCredential;
import io.camunda.zeebe.spring.common.auth.saas.SaaSAuthentication;
import io.camunda.zeebe.spring.common.auth.selfmanaged.SelfManagedAuthentication;
import io.camunda.zeebe.spring.common.auth.simple.SimpleAuthentication;
import io.camunda.zeebe.spring.common.auth.simple.SimpleConfig;
import io.camunda.zeebe.spring.common.json.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@EnableConfigurationProperties({
  CommonConfigurationProperties.class,
  ZeebeSelfManagedProperties.class
})
public class CommonClientConfiguration {

  @Autowired(required = false)
  CommonConfigurationProperties commonConfigurationProperties;

  @Autowired(required = false)
  ZeebeClientConfigurationProperties zeebeClientConfigurationProperties;

  @Autowired(required = false)
  ZeebeSelfManagedProperties zeebeSelfManagedProperties;

  @Autowired(required = false)
  private IdentityConfiguration identityConfigurationFromProperties;

  @Bean
  public Authentication authentication(final JsonMapper jsonMapper) {

    // TODO: Refactor
    if (zeebeClientConfigurationProperties != null) {
      // check if Zeebe has clusterId provided, then must be SaaS
      if (zeebeClientConfigurationProperties.getCloud().getClusterId() != null) {
        return SaaSAuthentication.builder()
            .withJwtConfig(configureJwtConfig())
            .withJsonMapper(jsonMapper)
            .build();
      } else if (zeebeClientConfigurationProperties.getBroker().getGatewayAddress() != null
          || zeebeSelfManagedProperties.getGatewayAddress() != null) {
        // figure out if Self-Managed JWT or Self-Managed Basic
        // Identity props take priority
        if (identityConfigurationFromProperties != null
            && hasText(identityConfigurationFromProperties.getClientId())) {
          final JwtConfig jwtConfig = configureJwtConfig();
          final IdentityConfig identityConfig = configureIdentities();
          return SelfManagedAuthentication.builder()
              .withJwtConfig(jwtConfig)
              .withIdentityConfig(identityConfig)
              .build();
        }

        // Fallback to common props
        if (commonConfigurationProperties != null
            && commonConfigurationProperties.getKeycloak().getUrl() != null) {
          final JwtConfig jwtConfig = configureJwtConfig();
          final IdentityConfig identityConfig = configureIdentities();
          return SelfManagedAuthentication.builder()
              .withJwtConfig(jwtConfig)
              .withIdentityConfig(identityConfig)
              .build();
        }
        if (commonConfigurationProperties != null
            && commonConfigurationProperties.getKeycloak().getTokenUrl() != null) {
          final JwtConfig jwtConfig = configureJwtConfig();
          final IdentityConfig identityConfig = configureIdentities();
          return SelfManagedAuthentication.builder()
              .withJwtConfig(jwtConfig)
              .withIdentityConfig(identityConfig)
              .build();
        }
        if (commonConfigurationProperties != null
            && commonConfigurationProperties.getUsername() != null
            && commonConfigurationProperties.getPassword() != null) {
          final SimpleConfig simpleConfig = new SimpleConfig();
          return SimpleAuthentication.builder()
              .withSimpleConfig(simpleConfig)
              .withSimpleUrl(commonConfigurationProperties.getUrl())
              .build();
        }
      }
    }
    return new DefaultNoopAuthentication();
  }

  private JwtConfig configureJwtConfig() {
    final JwtConfig jwtConfig = new JwtConfig();
    if (zeebeClientConfigurationProperties.getCloud().getClientId() != null
        && zeebeClientConfigurationProperties.getCloud().getClientSecret() != null) {
      jwtConfig.addProduct(
          Product.ZEEBE,
          new JwtCredential(
              zeebeClientConfigurationProperties.getCloud().getClientId(),
              zeebeClientConfigurationProperties.getCloud().getClientSecret(),
              zeebeClientConfigurationProperties.getCloud().getAudience(),
              zeebeClientConfigurationProperties.getCloud().getAuthUrl()));
    } else if (zeebeSelfManagedProperties.getClientId() != null
        && zeebeSelfManagedProperties.getClientSecret() != null) {
      jwtConfig.addProduct(
          Product.ZEEBE,
          new JwtCredential(
              zeebeSelfManagedProperties.getClientId(),
              zeebeSelfManagedProperties.getClientSecret(),
              zeebeSelfManagedProperties.getAudience(),
              zeebeSelfManagedProperties.getAuthServer()));
    } else if (commonConfigurationProperties.getClientId() != null
        && commonConfigurationProperties.getClientSecret() != null) {
      jwtConfig.addProduct(
          Product.ZEEBE,
          new JwtCredential(
              commonConfigurationProperties.getClientId(),
              commonConfigurationProperties.getClientSecret(),
              zeebeClientConfigurationProperties.getCloud().getAudience(),
              zeebeClientConfigurationProperties.getCloud().getAuthUrl()));
    }

    return jwtConfig;
  }

  private IdentityConfig configureIdentities() {
    final IdentityConfig identityConfig = new IdentityConfig();
    return identityConfig;
  }
}
