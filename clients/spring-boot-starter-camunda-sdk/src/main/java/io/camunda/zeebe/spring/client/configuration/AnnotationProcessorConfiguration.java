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

import io.camunda.zeebe.spring.client.annotation.customizer.ZeebeWorkerValueCustomizer;
import io.camunda.zeebe.spring.client.annotation.processor.ZeebeAnnotationProcessorRegistry;
import io.camunda.zeebe.spring.client.annotation.processor.ZeebeDeploymentAnnotationProcessor;
import io.camunda.zeebe.spring.client.annotation.processor.ZeebeWorkerAnnotationProcessor;
import io.camunda.zeebe.spring.client.event.ZeebeClientEventListener;
import io.camunda.zeebe.spring.client.jobhandling.JobWorkerManager;
import java.util.List;
import org.springframework.context.annotation.Bean;

public class AnnotationProcessorConfiguration {

  @Bean
  public static ZeebeAnnotationProcessorRegistry zeebeAnnotationProcessorRegistry() {
    return new ZeebeAnnotationProcessorRegistry();
  }

  @Bean
  public ZeebeClientEventListener zeebeClientEventListener(
      final ZeebeAnnotationProcessorRegistry zeebeAnnotationProcessorRegistry) {
    return new ZeebeClientEventListener(zeebeAnnotationProcessorRegistry);
  }

  @Bean
  public ZeebeDeploymentAnnotationProcessor deploymentPostProcessor() {
    return new ZeebeDeploymentAnnotationProcessor();
  }

  @Bean
  public ZeebeWorkerAnnotationProcessor zeebeWorkerPostProcessor(
      final JobWorkerManager jobWorkerManager,
      final List<ZeebeWorkerValueCustomizer> zeebeWorkerValueCustomizers) {
    return new ZeebeWorkerAnnotationProcessor(jobWorkerManager, zeebeWorkerValueCustomizers);
  }
}
