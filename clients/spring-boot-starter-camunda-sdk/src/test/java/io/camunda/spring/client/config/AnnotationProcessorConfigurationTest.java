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
package io.camunda.spring.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.spring.client.annotation.processor.AbstractCamundaAnnotationProcessor;
import io.camunda.spring.client.annotation.processor.CamundaAnnotationProcessorRegistry;
import io.camunda.spring.client.annotation.processor.DeploymentAnnotationProcessor;
import io.camunda.spring.client.annotation.processor.JobWorkerAnnotationProcessor;
import io.camunda.spring.client.configuration.AnnotationProcessorConfiguration;
import io.camunda.spring.client.jobhandling.JobWorkerManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes = AnnotationProcessorConfiguration.class)
public class AnnotationProcessorConfigurationTest {
  // required to auto-wire with the job worker annotation processor configuration
  @MockitoBean JobWorkerManager jobWorkerManager;

  @Autowired CamundaAnnotationProcessorRegistry registry;

  @Test
  void shouldRun() {
    final List<AbstractCamundaAnnotationProcessor> processors = registry.getProcessors();
    assertThat(processors).hasSize(2);
    assertThat(processors.get(0)).isInstanceOf(DeploymentAnnotationProcessor.class);
    assertThat(processors.get(1)).isInstanceOf(JobWorkerAnnotationProcessor.class);
  }
}
