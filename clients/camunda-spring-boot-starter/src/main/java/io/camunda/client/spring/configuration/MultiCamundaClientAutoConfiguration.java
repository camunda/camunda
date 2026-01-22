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
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.client.spring.configuration.condition.OnMultiClientConfigurationCondition;
import io.camunda.client.spring.event.MultiCamundaLifecycleEventProducer;
import io.camunda.client.spring.properties.MultiCamundaClientProperties;
import io.camunda.client.spring.testsupport.CamundaSpringProcessTestContext;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * Auto-configuration for multi-client Camunda setup.
 *
 * <p>This configuration is activated when {@code camunda.clients} is defined in the application
 * properties. It provides:
 *
 * <ul>
 *   <li>Dynamic registration of {@link CamundaClient} beans for each configured client
 *   <li>A {@link CamundaClientRegistry} bean for programmatic access to all clients
 *   <li>Job workers registered on ALL configured clients
 *   <li>Deployments executed on ALL configured clients
 * </ul>
 *
 * <p>Example configuration:
 *
 * <pre>
 * camunda:
 *   clients:
 *     production:
 *       primary: true
 *       rest-address: https://prod.camunda.io
 *       auth:
 *         method: oidc
 *         client-id: prod-client
 *         client-secret: ${PROD_CLIENT_SECRET}
 *     staging:
 *       rest-address: https://staging.camunda.io
 *       auth:
 *         method: oidc
 *         client-id: staging-client
 *         client-secret: ${STAGING_CLIENT_SECRET}
 * </pre>
 *
 * <p>Usage with injection:
 *
 * <pre>
 * // Inject the primary client (no qualifier needed)
 * &#64;Autowired
 * private CamundaClient camundaClient;
 *
 * // Inject a specific client by name
 * &#64;Autowired
 * &#64;Qualifier("stagingCamundaClient")
 * private CamundaClient stagingClient;
 *
 * // Use the registry to access clients dynamically
 * &#64;Autowired
 * private CamundaClientRegistry registry;
 *
 * public void doSomething() {
 *     CamundaClient client = registry.getClient("production");
 * }
 * </pre>
 */
@AutoConfiguration
@Conditional(OnMultiClientConfigurationCondition.class)
@EnableConfigurationProperties(MultiCamundaClientProperties.class)
@ImportAutoConfiguration({
  CamundaClientAllAutoConfiguration.class,
  CamundaActuatorConfiguration.class,
  MetricsDefaultConfiguration.class,
  JsonMapperConfiguration.class
})
public class MultiCamundaClientAutoConfiguration {

  /**
   * Registers the {@link MultiCamundaClientBeanDefinitionRegistryPostProcessor} which dynamically
   * creates CamundaClient beans based on the configuration.
   *
   * <p>This must be a static bean method to ensure it runs early in the Spring lifecycle, before
   * other beans are created.
   */
  @Bean
  public static MultiCamundaClientBeanDefinitionRegistryPostProcessor
      multiCamundaClientBeanDefinitionRegistryPostProcessor() {
    return new MultiCamundaClientBeanDefinitionRegistryPostProcessor();
  }

  /**
   * Creates a {@link CamundaClientRegistry} containing all registered CamundaClient instances.
   *
   * <p>The registry allows looking up clients by their configured name at runtime.
   *
   * @param properties the multi-client properties containing the configuration names
   * @param clients all CamundaClient beans, injected by Spring
   * @return a registry containing all clients
   */
  @Bean
  public CamundaClientRegistry camundaClientRegistry(
      final MultiCamundaClientProperties properties,
      @Autowired(required = false) final Map<String, CamundaClient> clients) {
    final Map<String, CamundaClient> namedClients = new HashMap<>();
    if (clients != null && properties.getClients() != null) {
      // Match config names to bean names using the naming convention
      properties
          .getClients()
          .keySet()
          .forEach(
              configName -> {
                final String beanName = toBeanName(configName) + "CamundaClient";
                final CamundaClient client = clients.get(beanName);
                if (client != null) {
                  namedClients.put(configName, client);
                }
              });
    }
    return new CamundaClientRegistry(namedClients);
  }

  /**
   * Creates the lifecycle event producer for multi-client mode.
   *
   * <p>This producer fires {@link io.camunda.client.spring.event.CamundaClientCreatedSpringEvent}
   * for each client in the registry, which triggers job worker registration and deployments on ALL
   * configured clients.
   *
   * @param registry the client registry containing all clients
   * @param publisher the Spring event publisher
   * @return the lifecycle event producer
   */
  @Bean
  @ConditionalOnMissingBean(CamundaSpringProcessTestContext.class)
  public MultiCamundaLifecycleEventProducer multiCamundaLifecycleEventProducer(
      final CamundaClientRegistry registry, final ApplicationEventPublisher publisher) {
    return new MultiCamundaLifecycleEventProducer(registry, publisher);
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
}
