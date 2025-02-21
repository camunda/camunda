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

import io.camunda.client.CamundaClient;
import io.camunda.spring.client.annotation.processor.AbstractCamundaAnnotationProcessor;
import io.camunda.spring.client.annotation.processor.CamundaAnnotationProcessorRegistry;
import io.camunda.spring.client.bean.ClassInfo;
import io.camunda.spring.client.configuration.AnnotationProcessorConfiguration;
import io.camunda.spring.client.jobhandling.JobWorkerManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = {
      AnnotationProcessorConfiguration.class,
      AnnotationProcessorConfigurationTest.TestConfig.class
    })
public class AnnotationProcessorConfigurationTest {
  // required to auto-wire with the job worker annotation processor configuration
  @MockitoBean JobWorkerManager jobWorkerManager;
  @MockitoBean CamundaClient camundaClient;
  @Autowired CamundaAnnotationProcessorRegistry registry;
  @Autowired MockedBean mockedBean;

  @Test
  void shouldRun() {
    // when - we mock the creation of the camunda client
    registry.startAll(camundaClient);
    // then
    final List<AbstractCamundaAnnotationProcessor> processors = registry.getProcessors();
    assertThat(processors)
        .anySatisfy(p -> assertThat(p).isInstanceOf(MockCamundaAnnotationProcessor.class));
    assertThat(mockedBean.isConfigured()).isTrue();
    assertThat(mockedBean.isStarted()).isTrue();
  }

  @Configuration
  static class TestConfig {
    @Bean
    public MockCamundaAnnotationProcessor mockCamundaAnnotationProcessor() {
      return new MockCamundaAnnotationProcessor();
    }

    @Bean
    public MockedBean mockedBean() {
      return new MockedBean();
    }
  }

  private static class MockCamundaAnnotationProcessor extends AbstractCamundaAnnotationProcessor {
    private MockedBean mockedBean;

    @Override
    public boolean isApplicableFor(final ClassInfo beanInfo) {
      return beanInfo.getBean() instanceof MockedBean;
    }

    @Override
    public void configureFor(final ClassInfo beanInfo) {
      final MockedBean bean = (MockedBean) beanInfo.getBean();
      bean.setConfigured(true);
      mockedBean = bean;
    }

    @Override
    public void start(final CamundaClient client) {
      mockedBean.setStarted(true);
    }

    @Override
    public void stop(final CamundaClient client) {}
  }

  private static class MockedBean {
    private boolean configured;
    private boolean started;

    public boolean isConfigured() {
      return configured;
    }

    public void setConfigured(final boolean configured) {
      this.configured = configured;
    }

    public boolean isStarted() {
      return started;
    }

    public void setStarted(final boolean started) {
      this.started = started;
    }
  }
}
