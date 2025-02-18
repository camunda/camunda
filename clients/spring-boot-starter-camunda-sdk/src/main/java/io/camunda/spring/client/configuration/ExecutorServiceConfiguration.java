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
package io.camunda.spring.client.configuration;

<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/main/java/io/camunda/spring/client/configuration/ExecutorServiceConfiguration.java
import static io.camunda.spring.client.configuration.CamundaClientConfigurationImpl.DEFAULT;
=======
import static io.camunda.zeebe.spring.client.configuration.PropertyUtil.getOrLegacyOrDefault;
import static io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties.DEFAULT;
import static java.util.Optional.ofNullable;
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/main/java/io/camunda/zeebe/spring/client/configuration/ExecutorServiceConfiguration.java

import io.camunda.spring.client.jobhandling.CamundaClientExecutorService;
import io.camunda.spring.client.properties.CamundaClientProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnMissingBean(CamundaClientExecutorService.class)
public class ExecutorServiceConfiguration {

  private final CamundaClientProperties camundaClientProperties;

  public ExecutorServiceConfiguration(final CamundaClientProperties camundaClientProperties) {
    this.camundaClientProperties = camundaClientProperties;
  }

  @Bean
<<<<<<< HEAD:clients/spring-boot-starter-camunda-sdk/src/main/java/io/camunda/spring/client/configuration/ExecutorServiceConfiguration.java
  public CamundaClientExecutorService camundaClientThreadPool(
      @Autowired(required = false) final MeterRegistry meterRegistry) {
    final int executionThreads =
        camundaClientProperties.getExecutionThreads() == null
            ? DEFAULT.getNumJobWorkerExecutionThreads()
            : camundaClientProperties.getExecutionThreads();
    final ScheduledExecutorService threadPool = Executors.newScheduledThreadPool(executionThreads);
=======
  public ZeebeClientExecutorService zeebeClientThreadPool(
      @Autowired(required = false) final MeterRegistry meterRegistry,
      final CamundaClientProperties camundaClientProperties) {
    final ScheduledExecutorService threadPool =
        Executors.newScheduledThreadPool(
            ofNullable(camundaClientProperties.getZeebe().getExecutionThreads())
                .orElse(ZeebeClientConfigurationImpl.DEFAULT.getNumJobWorkerExecutionThreads()));
>>>>>>> 94c106bd (feat: new property mapping mechanism, just like in 8.8):clients/spring-boot-starter-camunda-sdk/src/main/java/io/camunda/zeebe/spring/client/configuration/ExecutorServiceConfiguration.java
    if (meterRegistry != null) {
      final MeterBinder threadPoolMetrics =
          new ExecutorServiceMetrics(
              threadPool, "zeebe_client_thread_pool", Collections.emptyList());
      threadPoolMetrics.bindTo(meterRegistry);
    }
    return new CamundaClientExecutorService(threadPool, true);
  }
}
