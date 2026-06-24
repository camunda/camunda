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

import io.camunda.client.metrics.DefaultNoopMetricsRecorder;
import io.camunda.client.metrics.JobWorkerMetricsFactory;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.metrics.NoopJobWorkerMetricsFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

public class MetricsDefaultConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public MetricsRecorder noopMetricsRecorder() {
    return new DefaultNoopMetricsRecorder();
  }

  @Bean
  @ConditionalOnMissingBean
  public JobWorkerMetricsFactory jobWorkerMetricsFactory() {
    return new NoopJobWorkerMetricsFactory();
  }
}
