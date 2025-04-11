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

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.spring.client.configuration.condition.ConditionalOnCamundaClientEnabled;
import io.camunda.spring.client.testsupport.CamundaSpringProcessTestContext;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.impl.ZeebeClientImpl;
import io.camunda.zeebe.client.impl.util.ExecutorResource;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.camunda.zeebe.spring.client.event.ZeebeLifecycleEventProducer;
import io.grpc.ManagedChannel;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Deprecated(since = "8.6", forRemoval = true)
@Configuration
@ConditionalOnCamundaClientEnabled
@ConditionalOnMissingBean(CamundaSpringProcessTestContext.class)
@ConditionalOnBean(CamundaClientConfiguration.class)
public class ZeebeClientProdAutoConfiguration {
  private static final Logger LOG = LoggerFactory.getLogger(ZeebeClientProdAutoConfiguration.class);

  @Bean
  public ZeebeClientConfiguration zeebeClientConfiguration(
      final CamundaClientConfiguration camundaClientConfiguration) {
    return new ZeebeClientConfigurationImpl(camundaClientConfiguration);
  }

  @Bean(destroyMethod = "close")
  public ZeebeClient zeebeClient(final ZeebeClientConfiguration configuration) {
    LOG.info("Creating zeebeClient using zeebeClientConfiguration [{}]", configuration);
    final ScheduledExecutorService jobWorkerExecutor = configuration.jobWorkerExecutor();
    if (jobWorkerExecutor != null) {
      final ManagedChannel managedChannel = ZeebeClientImpl.buildChannel(configuration);
      final GatewayGrpc.GatewayStub gatewayStub =
          ZeebeClientImpl.buildGatewayStub(managedChannel, configuration);
      final ExecutorResource executorResource =
          new ExecutorResource(jobWorkerExecutor, configuration.ownsJobWorkerExecutor());
      return new ZeebeClientImpl(configuration, managedChannel, gatewayStub, executorResource);
    } else {
      return new ZeebeClientImpl(configuration);
    }
  }

  @Bean
  public ZeebeLifecycleEventProducer zeebeLifecycleEventProducer(
      final ZeebeClient client, final ApplicationEventPublisher publisher) {
    return new ZeebeLifecycleEventProducer(client, publisher);
  }
}
