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

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.configuration.JsonMapperConfiguration;
import io.camunda.zeebe.spring.client.configuration.MetricsDefaultConfiguration;
import io.camunda.zeebe.spring.client.configuration.ZeebeActuatorConfiguration;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientAllAutoConfiguration;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientProdAutoConfiguration;
import io.camunda.zeebe.spring.client.configuration.condition.ConditionalOnCamundaClientEnabled;
import io.camunda.zeebe.spring.client.event.ZeebeLifecycleEventProducer;
import io.camunda.zeebe.spring.client.testsupport.SpringZeebeTestContext;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4 compatible auto-configuration. Uses the new package location for
 * JacksonAutoConfiguration in Spring Boot 4.x.
 *
 * <p>Enabled by META-INF of Spring Boot Starter to provide beans for Camunda Clients
 */
@Configuration
@ConditionalOnCamundaClientEnabled
@ImportAutoConfiguration({
  ZeebeClientProdAutoConfiguration.class,
  ZeebeClientAllAutoConfiguration.class,
  ZeebeActuatorConfiguration.class,
  MetricsDefaultConfiguration.class,
  JsonMapperConfiguration.class
})
@AutoConfigureAfter(JacksonAutoConfiguration.class)
public class CamundaAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(
      SpringZeebeTestContext
          .class) // only run if we are not running in a test case - as otherwise the lifecycle
  // is controlled by the test
  public ZeebeLifecycleEventProducer zeebeLifecycleEventProducer(
      final ZeebeClient client, final ApplicationEventPublisher publisher) {
    return new ZeebeLifecycleEventProducer(client, publisher);
  }
}
