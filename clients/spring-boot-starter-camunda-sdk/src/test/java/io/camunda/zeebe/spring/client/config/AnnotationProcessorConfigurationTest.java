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
package io.camunda.zeebe.spring.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.spring.client.annotation.processor.AbstractZeebeAnnotationProcessor;
import io.camunda.zeebe.spring.client.annotation.processor.ZeebeAnnotationProcessorRegistry;
import io.camunda.zeebe.spring.client.annotation.processor.ZeebeDeploymentAnnotationProcessor;
import io.camunda.zeebe.spring.client.annotation.processor.ZeebeWorkerAnnotationProcessor;
import io.camunda.zeebe.spring.client.configuration.AnnotationProcessorConfiguration;
import io.camunda.zeebe.spring.client.jobhandling.JobWorkerManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes = AnnotationProcessorConfiguration.class)
public class AnnotationProcessorConfigurationTest {
  // required to auto-wire with the job worker annotation processor configuration
  @MockitoBean JobWorkerManager jobWorkerManager;

  @Autowired ZeebeAnnotationProcessorRegistry registry;

  @Test
  void shouldRun() {
    final List<AbstractZeebeAnnotationProcessor> processors = registry.getProcessors();
    assertThat(processors).hasSize(2);
    assertThat(processors)
        .anySatisfy(p -> assertThat(p).isInstanceOf(ZeebeWorkerAnnotationProcessor.class));
    assertThat(processors)
        .anySatisfy(p -> assertThat(p).isInstanceOf(ZeebeDeploymentAnnotationProcessor.class));
  }
}
