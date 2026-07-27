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
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.worker.JobExceptionHandler;
import io.camunda.client.jobhandling.CamundaClientExecutorService;
import io.camunda.client.jobhandling.JobExceptionHandlerSupplier;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.client.spring.bean.DefaultCamundaClientRegistry;
import io.camunda.client.spring.configuration.condition.ConditionalOnCamundaClientEnabled;
import io.camunda.client.spring.event.MultiCamundaLifecycleEventProducer;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.client.spring.properties.MultiCamundaClientProperties;
import io.camunda.client.spring.properties.MultiCamundaClientPropertiesResolver;
import io.camunda.client.spring.testsupport.CamundaSpringProcessTestContext;
import io.grpc.ClientInterceptor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

/**
 * The single auto-configuration path for the starter. Every application is a multi-client
 * application: a traditional single-client setup ({@code camunda.client.*}) is projected by {@link
 * io.camunda.client.spring.properties.CamundaClientPropertiesPostProcessor} onto one client named
 * {@code default}, and explicit {@code camunda.clients.<name>.*} entries add further clients.
 *
 * <p>It registers one {@link CamundaClient} bean per configured client (via {@link
 * MultiCamundaClientBeanDefinitionRegistryPostProcessor}, the primary/default one also aliased as
 * {@code camundaClient}), exposes a {@link CamundaClientRegistry}, and wires full feature parity
 * (job workers, deployment, {@code @ClusterVariables}, actuator, metrics, lifecycle events).
 *
 * <p>Disabled when {@code camunda.client.enabled} is {@code false}.
 */
@AutoConfiguration
@ConditionalOnCamundaClientEnabled
@ImportAutoConfiguration({
  // full feature parity with the single-client path: job-worker registration, startup deployment,
  // @ClusterVariables, JsonMapper, the actuator endpoint and metrics. None of these define a
  // CamundaClient bean (the per-client beans come from the registry post-processor); the shared
  // worker infrastructure binds its configuration from the camunda.client.* base.
  CamundaClientAllAutoConfiguration.class,
  CamundaActuatorConfiguration.class,
  MetricsDefaultConfiguration.class
})
public class CamundaAutoConfiguration {

  /**
   * Publishes one created/closing lifecycle event per configured client so the annotation
   * processors register their work (e.g. {@code @JobWorker}s) on every client. Skipped in tests,
   * where the lifecycle is driven by {@link CamundaSpringProcessTestContext}.
   */
  @Bean
  @ConditionalOnMissingBean(CamundaSpringProcessTestContext.class)
  public MultiCamundaLifecycleEventProducer multiCamundaLifecycleEventProducer(
      final CamundaClientRegistry registry, final ApplicationEventPublisher publisher) {
    return new MultiCamundaLifecycleEventProducer(registry, publisher);
  }

  /**
   * Exposes the primary (default) client's {@link CamundaClientConfiguration} so {@code @Autowired
   * CamundaClientConfiguration} keeps working as on the single-client path. Built from the primary
   * client's resolved properties (independent of the client bean), reusing the shared executor.
   * Lazy so no executor threads are created for applications that never inject it. Registered only
   * when a primary client is resolvable (a single client, or several with a designated primary) —
   * see {@link OnResolvablePrimaryClientCondition} — so it never resolves to a {@code null} bean.
   */
  @Bean
  @Lazy
  @ConditionalOnMissingBean
  @Conditional(OnResolvablePrimaryClientCondition.class)
  public CamundaClientConfiguration camundaClientConfiguration(
      final MultiCamundaClientProperties properties,
      final CredentialsProviderConfiguration credentialsProviderConfiguration,
      final ObjectProvider<JsonMapper> jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final CamundaClientExecutorService camundaClientExecutorService,
      final ObjectProvider<JobExceptionHandlerSupplier> jobExceptionHandlerSupplier) {
    final String primaryClientName = properties.getPrimaryClientName().orElseThrow();
    final CamundaClientProperties primaryProperties =
        properties.getClients().get(primaryClientName);
    return new SpringCamundaClientConfiguration(
        primaryProperties,
        jsonMapper.getIfAvailable(),
        interceptors,
        chainHandlers,
        camundaClientExecutorService,
        credentialsProviderConfiguration.camundaClientCredentialsProvider(primaryProperties),
        // prefer the context's supplier (so user overrides are reflected), matching the clients
        // built by CamundaClientFactory
        jobExceptionHandlerSupplier.getIfAvailable(
            () -> context -> JobExceptionHandler.createDefault()));
  }

  @Bean
  @ConditionalOnMissingBean
  public MultiCamundaClientProperties multiCamundaClientProperties(final Environment environment) {
    return MultiCamundaClientPropertiesResolver.resolve(environment);
  }

  /**
   * The credential-provider builder, reused per client to derive its {@link
   * io.camunda.client.CredentialsProvider} from the entry's {@code auth.*}. Registered as a plain
   * bean (not imported as a configuration) so its {@code @Bean} methods, which need the
   * single-client {@code CamundaClientProperties}, are not invoked in multi-client mode.
   */
  @Bean
  @ConditionalOnMissingBean
  public CredentialsProviderConfiguration credentialsProviderConfiguration() {
    return new CredentialsProviderConfiguration();
  }

  @Bean
  @ConditionalOnMissingBean
  public CamundaClientFactory camundaClientFactory(
      final CredentialsProviderConfiguration credentialsProviderConfiguration,
      final ObjectProvider<JsonMapper> jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final ObjectProvider<CamundaClientExecutorService> executorService,
      final ObjectProvider<JobExceptionHandlerSupplier> jobExceptionHandlerSupplier) {
    return new CamundaClientFactory(
        credentialsProviderConfiguration,
        jsonMapper,
        interceptors,
        chainHandlers,
        executorService,
        jobExceptionHandlerSupplier);
  }

  /**
   * Registers one {@link CamundaClient} bean per configured client. Must be {@code static} so it is
   * instantiated early enough to contribute bean definitions before regular beans are created.
   */
  @Bean
  public static MultiCamundaClientBeanDefinitionRegistryPostProcessor
      multiCamundaClientBeanDefinitionRegistryPostProcessor() {
    return new MultiCamundaClientBeanDefinitionRegistryPostProcessor();
  }

  @Bean
  @ConditionalOnMissingBean
  public CamundaClientRegistry camundaClientRegistry(
      final MultiCamundaClientProperties properties, final BeanFactory beanFactory) {
    // map each configured client name to its registered bean name (<name>CamundaClient); the client
    // beans are resolved lazily from the bean factory only when actually requested, so injecting
    // the
    // registry does not eagerly instantiate every client
    final Map<String, String> beanNamesByClientName = new LinkedHashMap<>();
    for (final String configName : properties.getClients().keySet()) {
      beanNamesByClientName.put(
          configName,
          MultiCamundaClientBeanDefinitionRegistryPostProcessor.beanNameFor(configName));
    }
    return new DefaultCamundaClientRegistry(
        beanNamesByClientName,
        beanName -> beanFactory.getBean(beanName, CamundaClient.class),
        properties.getPrimaryClientName().orElse(null));
  }
}
