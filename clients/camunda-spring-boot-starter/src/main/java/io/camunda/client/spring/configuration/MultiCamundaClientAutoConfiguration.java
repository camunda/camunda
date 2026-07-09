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

import io.camunda.client.spring.configuration.condition.ConditionalOnCamundaClientEnabled;
import io.camunda.client.spring.configuration.condition.OnMultiClientConfigurationCondition;
import io.camunda.client.spring.properties.MultiCamundaClientProperties;
import io.camunda.client.spring.properties.MultiCamundaClientPropertiesResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;

/**
 * Enables the generic multi-client configuration ({@code camunda.clients.<name>.*}) when at least
 * one client is configured under that prefix. Exposes the resolved per-client {@link
 * MultiCamundaClientProperties}; the registry and per-client beans are wired by a subsequent
 * configuration.
 *
 * <p>Like the single-client configuration, this is disabled when {@code camunda.client.enabled} is
 * {@code false}.
 */
@AutoConfiguration
@ConditionalOnCamundaClientEnabled
@Conditional(OnMultiClientConfigurationCondition.class)
public class MultiCamundaClientAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public MultiCamundaClientProperties multiCamundaClientProperties(final Environment environment) {
    return MultiCamundaClientPropertiesResolver.resolve(environment);
  }
}
