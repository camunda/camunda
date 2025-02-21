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
package io.camunda.zeebe.spring.client.annotation.processor;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.bean.ClassInfo;
import io.camunda.zeebe.spring.client.configuration.AnnotationProcessorConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

/**
 * Always created by {@link AnnotationProcessorConfiguration}
 *
 * <p>Keeps a list of all annotations and reads them after all Spring beans are initialized
 */
public class ZeebeAnnotationProcessorRegistry implements BeanPostProcessor, Ordered {
  private static final Logger LOG = LoggerFactory.getLogger(ZeebeAnnotationProcessorRegistry.class);

  private final List<AbstractZeebeAnnotationProcessor> processors = new ArrayList<>();
  private final List<ClassInfo> beans = new ArrayList<>();

  @Override
  public Object postProcessAfterInitialization(final Object bean, final String beanName)
      throws BeansException {
    if (bean instanceof final AbstractZeebeAnnotationProcessor processor) {
      processors.add(processor);
    } else {
      beans.add(ClassInfo.builder().bean(bean).beanName(beanName).build());
    }
    return bean;
  }

  public List<AbstractZeebeAnnotationProcessor> getProcessors() {
    // do not manipulate the list from outside
    return new ArrayList<>(processors);
  }

  public void startAll(final ZeebeClient client) {
    processBeans();
    processors.forEach(zeebePostProcessor -> zeebePostProcessor.start(client));
  }

  public void stopAll(final ZeebeClient client) {
    processors.forEach(zeebePostProcessor -> zeebePostProcessor.stop(client));
  }

  private void processBeans() {
    beans.forEach(
        bean -> {
          for (final AbstractZeebeAnnotationProcessor zeebePostProcessor : processors) {
            if (zeebePostProcessor.isApplicableFor(bean)) {
              zeebePostProcessor.configureFor(bean);
            }
          }
        });
    beans.clear();
  }

  @Override
  public int getOrder() {
    return LOWEST_PRECEDENCE;
  }
}
