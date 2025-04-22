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
public class ZeebeAnnotationProcessorRegistry implements BeanPostProcessor, Ordered {
  private static final Logger LOG = LoggerFactory.getLogger(ZeebeAnnotationProcessorRegistry.class);

  private final Set<AbstractZeebeAnnotationProcessor> processors = new HashSet<>();
  private final Map<String, Object> beans = new HashMap<>();

  @Override
  public Object postProcessAfterInitialization(final Object bean, final String beanName)
      throws BeansException {
    if (bean instanceof final AbstractZeebeAnnotationProcessor processor) {
      LOG.debug("Found processor: {}", beanName);
      processors.add(processor);
    } else {
      beans.put(beanName, bean);
    }
    return bean;
  }

  public List<AbstractZeebeAnnotationProcessor> getProcessors() {
    // do not manipulate the list from outside
    return new ArrayList<>(processors);
  }

  public void startAll(final ZeebeClient zeebeClient) {
    processBeans();
    processors.forEach(zeebePostProcessor -> zeebePostProcessor.start(zeebeClient));
  }

  public void stopAll(final ZeebeClient zeebeClient) {
    processors.forEach(zeebePostProcessor -> zeebePostProcessor.stop(zeebeClient));
  }

  private void processBeans() {
    beans.forEach(
        (beanName, bean) -> {
          final ClassInfo classInfo = ClassInfo.builder().bean(bean).beanName(beanName).build();
          for (final AbstractZeebeAnnotationProcessor zeebePostProcessor : processors) {
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
