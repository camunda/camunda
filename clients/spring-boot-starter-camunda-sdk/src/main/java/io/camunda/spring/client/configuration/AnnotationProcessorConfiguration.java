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

import io.camunda.spring.client.annotation.processor.CamundaClientLifecycleAware;
import io.camunda.spring.client.annotation.processor.DeploymentAnnotationProcessor;
import io.camunda.spring.client.annotation.processor.JobWorkerAnnotationProcessor;
import io.camunda.spring.client.event.CamundaClientEventListener;
import io.camunda.spring.client.jobhandling.JobWorkerManager;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

public class AnnotationProcessorConfiguration {

  @Bean
  public CamundaClientEventListener camundaClientEventListener(
      final Set<CamundaClientLifecycleAware> camundaClientLifecycleAwareSet) {
    return new CamundaClientEventListener(camundaClientLifecycleAwareSet);
  }

  @Bean
  @ConditionalOnProperty(value = "camunda.client.deployment.enabled", matchIfMissing = true)
  public DeploymentAnnotationProcessor deploymentPostProcessor(
      final ApplicationEventPublisher publisher) {
    return new DeploymentAnnotationProcessor(publisher);
  }

  @Bean
  public JobWorkerAnnotationProcessor jobWorkerPostProcessor(
      final JobWorkerManager jobWorkerManager) {
    return new JobWorkerAnnotationProcessor(jobWorkerManager);
  }
}
