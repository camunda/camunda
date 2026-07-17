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
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.client.spring.properties.MultiCamundaClientProperties;
import io.camunda.client.spring.properties.MultiCamundaClientPropertiesResolver;
import io.camunda.client.spring.testsupport.CamundaSpringProcessTestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * Registers one {@link CamundaClient} bean per client configured under {@code
 * camunda.clients.<name>.*}, named {@code <name>CamundaClient} (kebab-case names are camel-cased
 * for the bean-name prefix).
 *
 * <p>Each bean is built lazily via {@link CamundaClientFactory} from the resolved {@link
 * MultiCamundaClientProperties}, with {@code destroy-method=close} so the client is shut down on
 * context close. The designated primary client (see {@link
 * MultiCamundaClientProperties#getPrimaryClientName()}) is marked {@code @Primary} so a plain
 * {@code @Autowired CamundaClient} resolves to it.
 *
 * <p>Registered as a {@code static} bean by {@link CamundaAutoConfiguration} so it runs before
 * regular beans are instantiated.
 */
public class MultiCamundaClientBeanDefinitionRegistryPostProcessor
    implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, BeanFactoryAware {

  private static final String BEAN_NAME_SUFFIX = "CamundaClient";
  // historical single-client bean name, preserved as an alias for the primary client
  private static final String LEGACY_CLIENT_BEAN_NAME = "camundaClient";

  private static final Logger LOG =
      LoggerFactory.getLogger(MultiCamundaClientBeanDefinitionRegistryPostProcessor.class);

  private Environment environment;
  private BeanFactory beanFactory;

  @Override
  public void setEnvironment(final Environment environment) {
    this.environment = environment;
  }

  @Override
  public void setBeanFactory(final BeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  @Override
  public void postProcessBeanDefinitionRegistry(final BeanDefinitionRegistry registry)
      throws BeansException {
    if (isProcessTestSupportPresent(registry)) {
      // In process-test-support mode the test framework provides its own (primary, proxied)
      // CamundaClient; registering per-client beans here would create a second @Primary
      // CamundaClient and break the context. Mirrors the single-client path, whose client bean was
      // @ConditionalOnMissingBean(CamundaSpringProcessTestContext.class).
      LOG.debug(
          "CamundaSpringProcessTestContext present; skipping multi-client CamundaClient bean "
              + "registration (the test framework provides the client)");
      return;
    }
    final MultiCamundaClientProperties properties =
        MultiCamundaClientPropertiesResolver.resolve(environment);
    if (properties.getClients().isEmpty()) {
      return;
    }
    LOG.debug("Registering CamundaClient beans for clients {}", properties.getClients().keySet());

    final String primaryClientName = properties.getPrimaryClientName().orElse(null);
    // the sole (default) client reuses the shared context executor bean (so a VirtualThreads
    // override applies); with several clients each gets its own owned executor (see the factory)
    final boolean singleClient = properties.getClients().size() == 1;

    properties
        .getClients()
        .forEach(
            (name, clientProperties) ->
                registerClientBean(
                    registry,
                    name,
                    clientProperties,
                    name.equals(primaryClientName),
                    singleClient));
  }

  @Override
  public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory)
      throws BeansException {
    // no-op: only bean definitions are registered
  }

  private static boolean isProcessTestSupportPresent(final BeanDefinitionRegistry registry) {
    // the process-test-support auto-configuration registers a CamundaSpringProcessTestContext bean
    // before this post-processor runs; detect it by type without instantiating any bean
    return registry instanceof final ListableBeanFactory beanFactory
        && beanFactory.getBeanNamesForType(CamundaSpringProcessTestContext.class, false, false)
                .length
            > 0;
  }

  private void registerClientBean(
      final BeanDefinitionRegistry registry,
      final String name,
      final CamundaClientProperties properties,
      final boolean primary,
      final boolean useSharedExecutor) {
    // resolve the CamundaClientFactory bean lazily, at client instantiation time (the factory bean
    // does not exist yet while bean definitions are being registered)
    final BeanDefinitionBuilder builder =
        BeanDefinitionBuilder.genericBeanDefinition(
                CamundaClient.class,
                () ->
                    beanFactory
                        .getBean(CamundaClientFactory.class)
                        .createClient(name, properties, useSharedExecutor))
            .setDestroyMethodName("close");
    final AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();
    beanDefinition.setPrimary(primary);
    // build the client (and open its connection) only on first use, not eagerly at context refresh
    beanDefinition.setLazyInit(true);

    final String beanName = beanNameFor(name);
    registry.registerBeanDefinition(beanName, beanDefinition);
    // preserve the historical public bean name: the primary client is also reachable as
    // 'camundaClient', so @Qualifier("camundaClient") / getBean("camundaClient") keep working
    if (primary
        && !LEGACY_CLIENT_BEAN_NAME.equals(beanName)
        && !registry.isBeanNameInUse(LEGACY_CLIENT_BEAN_NAME)) {
      registry.registerAlias(beanName, LEGACY_CLIENT_BEAN_NAME);
    }
    LOG.debug("Registered CamundaClient bean '{}' (primary={})", beanName, primary);
  }

  /**
   * The bean name for a configured client: the raw client name suffixed with {@code CamundaClient}.
   * The name is used verbatim (no kebab-to-camel transform) so distinct client names always map to
   * distinct bean names — bean names may contain dashes and {@code @Qualifier} accepts any string.
   */
  static String beanNameFor(final String clientName) {
    return clientName + BEAN_NAME_SUFFIX;
  }
}
