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
package io.camunda.client.spring.annotation.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.client.CamundaClient;
import io.camunda.client.bean.BeanInfo;
import io.camunda.client.spring.annotation.processor.AbstractCamundaAnnotationProcessorTest.PrototypeBeanConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@SpringBootTest(classes = PrototypeBeanConfig.class)
public class AbstractCamundaAnnotationProcessorTest {

  @Autowired TestCamundaAnnotationProcessor processor;

  @Test
  void shouldConfigureWithoutBeanInit() {
    processor.onStart(mock(CamundaClient.class));
    assertThat(processor.configured).isTrue();
  }

  @Configuration
  static class PrototypeBeanConfig {
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public SomeBean someBean() {
      return new SomeBean();
    }

    @Bean
    public TestCamundaAnnotationProcessor processor() {
      return new TestCamundaAnnotationProcessor();
    }
  }

  static class SomeBean {
    public SomeBean() {
      throw new IllegalStateException("must never be called");
    }
  }

  static class TestCamundaAnnotationProcessor extends AbstractCamundaAnnotationProcessor {
    private boolean configured = false;

    @Override
    protected boolean isApplicableFor(final BeanInfo beanInfo) {
      return beanInfo.getTargetClass().equals(SomeBean.class);
    }

    @Override
    protected void configureFor(final BeanInfo beanInfo) {
      configured = true;
    }

    @Override
    protected void start(final CamundaClient client) {
      // do nothing
    }

    @Override
    protected void stop(final CamundaClient client) {
      // do nothing
    }
  }
}
