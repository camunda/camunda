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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
public class CamundaAnnotationProcessorRegistry implements BeanPostProcessor, Ordered {
  private static final Logger LOG =
      LoggerFactory.getLogger(CamundaAnnotationProcessorRegistry.class);
  private final Set<AbstractCamundaAnnotationProcessor> processors = new HashSet<>();
  private final Map<String, Object> beans = new HashMap<>();

  @Override
  public Object postProcessBeforeInitialization(final Object bean, final String beanName)
      throws BeansException {
    if (bean instanceof final AbstractCamundaAnnotationProcessor processor) {
      processors.add(processor);
    }
    return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
  }

  @Override
  public Object postProcessAfterInitialization(final Object bean, final String beanName)
      throws BeansException {
    if (bean instanceof final AbstractCamundaAnnotationProcessor processor) {
      LOG.debug("Found processor: {}", beanName);
      processors.add(processor);
    } else {
      beans.put(beanName, bean);
    }
    return bean;
  }

  public List<AbstractCamundaAnnotationProcessor> getProcessors() {
    // do not manipulate the list from outside
    return new ArrayList<>(processors);
  }

  public void startAll(final CamundaClient client) {
    processBeans();
    processors.forEach(camundaPostProcessor -> camundaPostProcessor.start(client));
  }

  public void stopAll(final CamundaClient client) {
    processors.forEach(camundaPostProcessor -> camundaPostProcessor.stop(client));
  }

  private void processBeans() {
    beans.forEach(
        (beanName, bean) -> {
          final ClassInfo classInfo = ClassInfo.builder().bean(bean).beanName(beanName).build();
          for (final AbstractCamundaAnnotationProcessor zeebePostProcessor : processors) {
            if (zeebePostProcessor.isApplicableFor(classInfo)) {
              LOG.debug(
                  "Configuring bean {} with post processor {}",
                  beanName,
                  zeebePostProcessor.getBeanName());
              zeebePostProcessor.configureFor(classInfo);
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
