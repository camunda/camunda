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

import io.camunda.client.CamundaClient;
import io.camunda.spring.client.configuration.condition.ConditionalOnCamundaClientEnabled;
import io.camunda.spring.client.event.CamundaLifecycleEventProducer;
import io.camunda.spring.client.testsupport.CamundaSpringProcessTestContext;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientProdAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Enabled by META-INF of Spring Boot Starter to provide beans for Camunda Clients */
@Configuration
@ConditionalOnCamundaClientEnabled
@ImportAutoConfiguration({
  CamundaClientProdAutoConfiguration.class,
  CamundaClientAllAutoConfiguration.class,
  CamundaActuatorConfiguration.class,
  MetricsDefaultConfiguration.class,
  JsonMapperConfiguration.class,
  ZeebeClientProdAutoConfiguration.class
})
@AutoConfigureAfter(JacksonAutoConfiguration.class)
public class CamundaAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(
      CamundaSpringProcessTestContext
          .class) // only run if we are not running in a test case - as otherwise the lifecycle
  // is controlled by the test
  public CamundaLifecycleEventProducer camundaLifecycleEventProducer(
      final CamundaClient client, final ApplicationEventPublisher publisher) {
    return new CamundaLifecycleEventProducer(client, publisher);
  }
}
