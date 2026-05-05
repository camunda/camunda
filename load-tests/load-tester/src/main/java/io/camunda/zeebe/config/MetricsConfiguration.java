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
package io.camunda.zeebe.config;

import io.grpc.ClientInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.grpc.MetricCollectingClientInterceptor;
import io.micrometer.prometheusmetrics.PrometheusRenameFilter;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfiguration {

  private final MeterRegistry registry;

  public MetricsConfiguration(final MeterRegistry registry) {
    this.registry = registry;
  }

  @PostConstruct
  void applyPrometheusRenameFilter() {
    registry.config().meterFilter(new PrometheusRenameFilter());
  }

  @Bean
  public ClientInterceptor grpcMetricsInterceptor() {
    return new MetricCollectingClientInterceptor(registry);
  }
}
