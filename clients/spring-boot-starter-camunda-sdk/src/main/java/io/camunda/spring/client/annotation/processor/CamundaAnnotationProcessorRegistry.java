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
package io.camunda.spring.client.annotation.processor;

import io.camunda.client.CamundaClient;
import io.camunda.spring.client.bean.ClassInfo;
import io.camunda.spring.client.configuration.AnnotationProcessorConfiguration;
import java.util.List;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

/**
 * Always created by {@link AnnotationProcessorConfiguration}
 *
 * <p>Keeps a list of all annotations and reads them after all Spring beans are initialized
 */
public class CamundaAnnotationProcessorRegistry implements BeanPostProcessor, Ordered {

  private final List<AbstractCamundaAnnotationProcessor> processors;

  public CamundaAnnotationProcessorRegistry(
      final List<AbstractCamundaAnnotationProcessor> processors) {
    this.processors = processors;
  }

  @Override
  public Object postProcessAfterInitialization(final Object bean, final String beanName)
      throws BeansException {
    final ClassInfo beanInfo = ClassInfo.builder().bean(bean).beanName(beanName).build();

    for (final AbstractCamundaAnnotationProcessor camundaPostProcessor : processors) {
      if (camundaPostProcessor.isApplicableFor(beanInfo)) {
        camundaPostProcessor.configureFor(beanInfo);
      }
    }

    return bean;
  }

  public void startAll(final CamundaClient client) {
    processors.forEach(camundaPostProcessor -> camundaPostProcessor.start(client));
  }

  public void stopAll(final CamundaClient client) {
    processors.forEach(camundaPostProcessor -> camundaPostProcessor.stop(client));
  }

  @Override
  public int getOrder() {
    return LOWEST_PRECEDENCE;
  }
}
