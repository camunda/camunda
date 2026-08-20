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
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.bean.BeanInfo;
import io.camunda.client.spring.annotation.processor.CamundaAnnotationProcessorAopCompatibilityTest.AopBeanConfig;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootTest(classes = AopBeanConfig.class)
public class CamundaAnnotationProcessorAopCompatibilityTest {

  @Autowired TestCamundaAnnotationProcessor processor;
  @Autowired ExampleAspect exampleAspect;
  @Autowired MyWorker myWorker;

  @Test
  void shouldExtractTargetClassNotSpringProxy() {
    // given
    processor.onStart(mock(CamundaClient.class));

    // when
    myWorker.worker(null, null);

    // then
    // verify Aspect was working as expected
    assertThat(exampleAspect.invoked).isTrue();
    assertThat(myWorker.wasInvoked()).isTrue();
    // verify that processor extracted the correct target class not the CGLIB proxy class
    assertThat(processor.beanInfo)
        .isNotNull()
        .extracting(BeanInfo::getTargetClass)
        .isEqualTo(MyWorker.class);
  }

  @Configuration
  @EnableAspectJAutoProxy
  static class AopBeanConfig {
    @Bean
    public TestCamundaAnnotationProcessor processor() {
      return new TestCamundaAnnotationProcessor();
    }

    @Bean
    public MyWorker myWorker() {
      return new MyWorker();
    }

    @Bean
    public ExampleAspect exampleAspect() {
      return new ExampleAspect();
    }
  }

  static class MyWorker {
    boolean invoked = false;

    @JobWorker
    public void worker(final ActivatedJob job, @Variable final String name) {
      invoked = true;
    }

    public boolean wasInvoked() {
      return invoked;
    }
  }

  static class TestCamundaAnnotationProcessor extends AbstractCamundaAnnotationProcessor {
    BeanInfo beanInfo;

    @Override
    protected boolean isApplicableFor(final BeanInfo beanInfo) {
      return beanInfo.getTargetClass().equals(MyWorker.class);
    }

    @Override
    protected void configureFor(final BeanInfo beanInfo) {
      this.beanInfo = beanInfo;
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

  @Aspect
  static class ExampleAspect {
    boolean invoked = false;

    @Around("@annotation(io.camunda.client.annotation.JobWorker) && args(job,..)")
    public Object wrapJobWorker(final ProceedingJoinPoint joinPoint, final ActivatedJob job)
        throws Throwable {
      invoked = true;
      return joinPoint.proceed();
    }
  }
}
