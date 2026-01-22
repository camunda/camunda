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

import io.camunda.client.CamundaClient;
import io.camunda.client.spring.properties.CamundaClientConfigurationProperties;
import io.camunda.client.spring.properties.MultiCamundaClientProperties;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * A {@link BeanDefinitionRegistryPostProcessor} that dynamically registers {@link CamundaClient}
 * beans based on the multi-client configuration.
 *
 * <p>For each client configured under {@code camunda.clients.*}, this processor registers:
 *
 * <ul>
 *   <li>A {@link CamundaClient} bean with the name {@code <clientName>CamundaClient}
 * </ul>
 *
 * <p>If a client is marked as {@code primary: true}, its bean will be annotated as the primary
 * bean, allowing it to be autowired without a qualifier.
 *
 * <p>Example configuration:
 *
 * <pre>
 * camunda:
 *   clients:
 *     production:
 *       primary: true
 *       rest-address: https://prod.camunda.io
 *     staging:
 *       rest-address: https://staging.camunda.io
 * </pre>
 *
 * This creates beans:
 *
 * <ul>
 *   <li>{@code productionCamundaClient} (primary)
 *   <li>{@code stagingCamundaClient}
 * </ul>
 */
public class MultiCamundaClientBeanDefinitionRegistryPostProcessor
    implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

  private static final Logger LOG =
      LoggerFactory.getLogger(MultiCamundaClientBeanDefinitionRegistryPostProcessor.class);

  private static final String BEAN_NAME_SUFFIX = "CamundaClient";

  private Environment environment;
  private final Map<String, CamundaClientConfigurationProperties> clientConfigs = new HashMap<>();

  @Override
  public void setEnvironment(final Environment environment) {
    this.environment = environment;
  }

  @Override
  public void postProcessBeanDefinitionRegistry(final BeanDefinitionRegistry registry)
      throws BeansException {
    final MultiCamundaClientProperties properties = bindProperties();

    if (!properties.isMultiClientEnabled()) {
      LOG.debug("Multi-client configuration not enabled, skipping dynamic bean registration");
      return;
    }

    LOG.info(
        "Registering {} CamundaClient beans: {}",
        properties.getClients().size(),
        properties.getClients().keySet());

    final CamundaClientFactory factory = new CamundaClientFactory();

    properties
        .getClients()
        .forEach(
            (name, clientProps) -> {
              if (!clientProps.isEnabled()) {
                LOG.debug("Skipping disabled client '{}'", name);
                return;
              }

              clientConfigs.put(name, clientProps);
              registerClientBean(registry, factory, name, clientProps);
            });
  }

  @Override
  public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory)
      throws BeansException {
    // No-op - we only need to register bean definitions
  }

  private MultiCamundaClientProperties bindProperties() {
    return Binder.get(environment)
        .bind("camunda", MultiCamundaClientProperties.class)
        .orElseGet(MultiCamundaClientProperties::new);
  }

  private void registerClientBean(
      final BeanDefinitionRegistry registry,
      final CamundaClientFactory factory,
      final String name,
      final CamundaClientConfigurationProperties clientProps) {

    final String beanName = toBeanName(name) + BEAN_NAME_SUFFIX;

    final BeanDefinitionBuilder builder =
        BeanDefinitionBuilder.genericBeanDefinition(
            CamundaClient.class, () -> factory.createClient(name, clientProps));

    builder.setDestroyMethodName("close");

    final AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();

    if (clientProps.isPrimary()) {
      beanDefinition.setPrimary(true);
      LOG.debug("Marking client '{}' as primary", name);
    }

    registry.registerBeanDefinition(beanName, beanDefinition);
    LOG.debug("Registered CamundaClient bean '{}' for client '{}'", beanName, name);
  }

  /**
   * Converts a kebab-case configuration name to a camelCase bean name prefix. For example,
   * "my-client" becomes "myClient".
   */
  private String toBeanName(final String configName) {
    final StringBuilder result = new StringBuilder();
    boolean capitalizeNext = false;
    for (final char c : configName.toCharArray()) {
      if (c == '-') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }

  /**
   * Returns the client configurations that were processed. Useful for creating the registry bean.
   */
  public Map<String, CamundaClientConfigurationProperties> getClientConfigs() {
    return clientConfigs;
  }
}
