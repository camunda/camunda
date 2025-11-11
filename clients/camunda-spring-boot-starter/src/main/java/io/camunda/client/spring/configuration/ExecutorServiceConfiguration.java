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

import io.camunda.client.jobhandling.CamundaClientExecutorService;
import io.camunda.client.metrics.MeteredCamundaClientExecutorService;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.micrometer.core.instrument.MeterRegistry;
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
  public CamundaClientExecutorService meteredCamundaClientThreadPool(
      @Autowired(required = false) final MeterRegistry meterRegistry) {
    return MeteredCamundaClientExecutorService.createDefault(
        camundaClientProperties.getExecutionThreads(), meterRegistry);
  }
}
