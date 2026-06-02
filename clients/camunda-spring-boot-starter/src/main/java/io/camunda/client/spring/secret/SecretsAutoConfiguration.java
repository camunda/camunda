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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.secret.SecretsClient;
import io.camunda.client.impl.secret.SecretResolvingJsonMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

/**
 * Layers transparent {@code camunda.secrets.*} resolution on top of whatever {@link JsonMapper}
 * bean the application already exposes. Disabled by default; enable with {@code
 * camunda.client.secrets.transparent-resolution=true}.
 *
 * <p>Activation is purely additive:
 *
 * <ul>
 *   <li>A {@link BeanPostProcessor} wraps the existing {@link JsonMapper} bean (default or
 *       user-provided) with a {@link SecretResolvingJsonMapper}, so a user's custom Jackson
 *       modules, naming strategies, and serialization features are preserved.
 *   <li>A default {@link SecretsClient} is provided only if the user has not defined their own
 *       (e.g. for tests or alternative backends). The default delegates to {@link
 *       CamundaClient#newSecretResolveCommand()}.
 *   <li>The {@link CamundaClient} is injected lazily so the wrapper can be assembled before the
 *       client itself is fully constructed.
 * </ul>
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

  /**
   * Wraps every {@link JsonMapper} bean with {@link SecretResolvingJsonMapper}. {@code static} so
   * Spring can instantiate the post-processor before any regular bean is created without forcing
   * eager creation of the enclosing configuration class. {@link ObjectProvider} defers {@link
   * SecretsClient} lookup until first use, avoiding ordering issues.
   */
  @Bean
  static BeanPostProcessor secretResolvingJsonMapperWrapper(
      final ObjectProvider<SecretsClient> secretsClient) {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(final Object bean, final String beanName) {
        if (bean instanceof JsonMapper && !(bean instanceof SecretResolvingJsonMapper)) {
          return new SecretResolvingJsonMapper(
              (JsonMapper) bean, references -> secretsClient.getObject().resolve(references));
        }
        return bean;
      }
    };
  }
}
