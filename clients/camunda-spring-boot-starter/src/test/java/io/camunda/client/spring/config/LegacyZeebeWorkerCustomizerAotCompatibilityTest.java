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
package io.camunda.client.spring.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.client.spring.configuration.AnnotationProcessorConfiguration;
import io.camunda.client.spring.event.CamundaClientCreatedSpringEvent;
import io.camunda.client.spring.event.CamundaClientEventListener;
import io.camunda.zeebe.spring.client.annotation.customizer.ZeebeWorkerValueCustomizer;
import io.camunda.zeebe.spring.client.annotation.value.ZeebeWorkerValue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Regression test for https://github.com/camunda/camunda/issues/49131.
 *
 * <p>Verifies that a legacy {@link ZeebeWorkerValueCustomizer} bean is applied to job workers even
 * when {@code LegacyJobWorkerValueCustomizerBeanDefinitionRegistryPostProcessor} does not register
 * the compat wrapper as a bean — which is the scenario that occurs with Spring AOT, where the
 * dynamic {@code BeanDefinitionRegistryPostProcessor} bean registration produces a null-customizer
 * {@code JobWorkerValueCustomizerCompat} due to an unresolved {@code RuntimeBeanReference}.
 */
@SpringBootTest(
    classes = {
      AnnotationProcessorConfiguration.class,
      LegacyZeebeWorkerCustomizerAotCompatibilityTest.TestConfig.class
    })
class LegacyZeebeWorkerCustomizerAotCompatibilityTest {

  // No CamundaBeanPostProcessorConfiguration — simulates the AOT scenario where the
  // BeanDefinitionRegistryPostProcessor does not register compat beans.
  @MockitoBean JobWorkerManager jobWorkerManager;
  @MockitoBean CamundaClient camundaClient;

  @Autowired CamundaClientEventListener camundaClientEventListener;
  @Autowired TestConfig.TrackingZeebeCustomizer trackingCustomizer;

  @Test
  void shouldApplyLegacyZeebeWorkerValueCustomizerWhenPostProcessorIsAbsent() {
    // when
    camundaClientEventListener.handleStart(
        new CamundaClientCreatedSpringEvent(this, mock(CamundaClient.class)));

    // then — the legacy customizer must have been invoked via the compat wrapper,
    // proving AnnotationProcessorConfiguration handles it directly
    assertThat(trackingCustomizer.wasCalled()).isTrue();
  }

  @Configuration
  static class TestConfig {

    @Bean
    public TrackingZeebeCustomizer legacyZeebeCustomizer() {
      return new TrackingZeebeCustomizer();
    }

    @Bean
    public WorkerBean workerBean() {
      return new WorkerBean();
    }

    static class TrackingZeebeCustomizer implements ZeebeWorkerValueCustomizer {
      private boolean called = false;

      @Override
      public void customize(final ZeebeWorkerValue worker) {
        called = true;
      }

      public boolean wasCalled() {
        return called;
      }
    }

    static class WorkerBean {
      @JobWorker(type = "test-type")
      public void handle(final ActivatedJob job) {}
    }
  }
}
