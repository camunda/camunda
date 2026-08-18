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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.bean.BeanInfo;
import io.camunda.client.jobhandling.JobCallbackCommandWrapperFactory;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.client.jobhandling.parameter.ParameterResolverStrategy;
import io.camunda.client.jobhandling.result.ResultProcessorStrategy;
import io.camunda.client.lifecycle.CamundaClientLifecycleAware;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.spring.annotation.processor.AbstractCamundaAnnotationProcessor;
import io.camunda.client.spring.annotation.processor.DeploymentAnnotationProcessor;
import io.camunda.client.spring.event.CamundaClientCreatedSpringEvent;
import io.camunda.client.spring.event.CamundaClientEventListener;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

public class AnnotationProcessorConfigurationTest {

  @Nested
  @SpringBootTest(
      classes = {
        AnnotationProcessorConfiguration.class,
        AnnotationProcessorConfigurationTest.TestConfig.class
      })
  class AnnotationProcessorLifecycleTest {
    @MockitoBean JobWorkerManager jobWorkerManager;
    @MockitoBean CamundaClient camundaClient;
    @MockitoBean JsonMapper jsonMapper;
    @MockitoBean JobCallbackCommandWrapperFactory jobCallbackCommandWrapperFactory;
    @MockitoBean MetricsRecorder metricsRecorder;
    @MockitoBean ParameterResolverStrategy parameterResolverStrategy;
    @MockitoBean ResultProcessorStrategy resultProcessorStrategy;
    @Autowired MockedBean mockedBean;
    @Autowired CamundaClientEventListener camundaClientEventListener;
    @Autowired Set<CamundaClientLifecycleAware> camundaClientLifecycleAwareSet;

    @Test
    void shouldRun() {
      // when - we mock the creation of the camunda client
      camundaClientEventListener.handleStart(
          new CamundaClientCreatedSpringEvent(this, camundaClient));
      // then
      assertThat(camundaClientLifecycleAwareSet)
          .anySatisfy(p -> assertThat(p).isInstanceOf(MockCamundaAnnotationProcessor.class));
      assertThat(mockedBean.isConfigured()).isTrue();
      assertThat(mockedBean.isStarted()).isTrue();
    }
  }

  @Nested
  @SpringBootTest(
      classes = {AnnotationProcessorConfiguration.class},
      properties = "camunda.client.cluster-variables.enabled=false")
  class DefaultResourcePatternResolverTest {
    @MockitoBean JobWorkerManager jobWorkerManager;
    @MockitoBean MetricsRecorder metricsRecorder;
    @MockitoBean ParameterResolverStrategy parameterResolverStrategy;
    @MockitoBean ResultProcessorStrategy resultProcessorStrategy;
    @MockitoBean JobCallbackCommandWrapperFactory jobCallbackCommandWrapperFactory;
    @Autowired ResourcePatternResolver resourcePatternResolver;
    @Autowired DeploymentAnnotationProcessor deploymentAnnotationProcessor;

    @Test
    void shouldCreatePathMatchingResourcePatternResolverByDefault() {
      assertThat(resourcePatternResolver).isInstanceOf(PathMatchingResourcePatternResolver.class);
      assertThat(deploymentAnnotationProcessor).isNotNull();
    }
  }

  @Nested
  // CustomResourcePatternResolverConfig is a user bean (processed first).
  // AnnotationProcessorConfiguration is loaded as autoconfiguration (processed after user beans),
  // so @ConditionalOnMissingBean correctly skips the default ResourcePatternResolver.
  @SpringBootTest(
      classes = {AnnotationProcessorConfigurationTest.CustomResourcePatternResolverConfig.class},
      properties = "camunda.client.cluster-variables.enabled=false")
  @ImportAutoConfiguration(AnnotationProcessorConfiguration.class)
  class CustomResourcePatternResolverTest {
    @MockitoBean JobWorkerManager jobWorkerManager;
    @MockitoBean MetricsRecorder metricsRecorder;
    @MockitoBean ParameterResolverStrategy parameterResolverStrategy;
    @MockitoBean ResultProcessorStrategy resultProcessorStrategy;
    @MockitoBean JobCallbackCommandWrapperFactory jobCallbackCommandWrapperFactory;
    @Autowired ResourcePatternResolver resourcePatternResolver;
    @Autowired DeploymentAnnotationProcessor deploymentAnnotationProcessor;

    @Test
    void shouldAllowOverridingResourcePatternResolverWithCustomBean() {
      // given a custom ResourcePatternResolver bean declared by the user,
      // the default PathMatchingResourcePatternResolver must be suppressed
      assertThat(resourcePatternResolver).isInstanceOf(CustomResourcePatternResolver.class);
      assertThat(deploymentAnnotationProcessor).isNotNull();
    }
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

  @Configuration
  static class CustomResourcePatternResolverConfig {
    @Bean
    public ResourcePatternResolver resourcePatternResolver() {
      return new CustomResourcePatternResolver();
    }
  }

  static final class CustomResourcePatternResolver extends PathMatchingResourcePatternResolver {}

  private static final class MockCamundaAnnotationProcessor
      extends AbstractCamundaAnnotationProcessor {
    private MockedBean mockedBean;

    @Override
    public boolean isApplicableFor(final BeanInfo beanInfo) {
      return beanInfo.getBeanSupplier().get() instanceof MockedBean;
    }

    @Override
    public void configureFor(final BeanInfo beanInfo) {
      final MockedBean bean = (MockedBean) beanInfo.getBeanSupplier().get();
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

  private static final class MockedBean {
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
