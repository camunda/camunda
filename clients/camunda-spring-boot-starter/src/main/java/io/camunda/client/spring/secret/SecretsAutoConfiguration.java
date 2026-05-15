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
package io.camunda.client.spring.secret;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.secret.SecretsClient;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.impl.secret.SecretResolvingJsonMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

/**
 * Wires transparent secret resolution into the Camunda Spring Boot client. Disabled by default;
 * enable with {@code camunda.client.secrets.transparent-resolution=true}.
 *
 * <p>When enabled, a {@link SecretResolvingJsonMapper} is registered as the primary {@link
 * JsonMapper}; it wraps the default {@link CamundaObjectMapper} and resolves {@code
 * camunda.secrets.*} references on each variable deserialization by delegating to {@link
 * CamundaClient#newSecretResolveCommand()}. The {@link CamundaClient} is injected lazily to avoid a
 * circular dependency between the client and the mapper it uses.
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "camunda.client.secrets",
    name = "transparent-resolution",
    havingValue = "true")
public class SecretsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public SecretsClient secretsClient(@Lazy final CamundaClient camundaClient) {
    return SecretsClient.fromCamundaClient(camundaClient);
  }

  @Bean
  @Primary
  public JsonMapper secretResolvingJsonMapper(
      final ObjectMapper objectMapper, final SecretsClient secretsClient) {
    return new SecretResolvingJsonMapper(
        new CamundaObjectMapper(objectMapper.copy()), secretsClient);
  }
}
