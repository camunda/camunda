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

import static java.util.Optional.ofNullable;

import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.impl.NoopCredentialsProvider;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import java.net.URI;
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
    if (camundaClientProperties.getMode() == null) {
      return new NoopCredentialsProvider();
    }
    final OAuthCredentialsProviderBuilder credBuilder =
        CredentialsProvider.newCredentialsProviderBuilder()
            .applyEnvironmentOverrides(false)
            .clientId(camundaClientProperties.getAuth().getClientId())
            .clientSecret(camundaClientProperties.getAuth().getClientSecret())
            .audience(camundaClientProperties.getZeebe().getAudience())
            .scope(camundaClientProperties.getZeebe().getScope())
            .authorizationServerUrl(
                ofNullable(camundaClientProperties.getAuth().getTokenUrl())
                    .map(URI::toString)
                    .orElse(null))
            .credentialsCachePath(camundaClientProperties.getAuth().getCredentialsCachePath())
            .connectTimeout(camundaClientProperties.getAuth().getConnectTimeout())
            .readTimeout(camundaClientProperties.getAuth().getReadTimeout());
    try {
      return credBuilder.build();
    } catch (final Exception e) {
      LOG.warn("Failed to configure credential provider", e);
      return new NoopCredentialsProvider();
    }
  }
}
