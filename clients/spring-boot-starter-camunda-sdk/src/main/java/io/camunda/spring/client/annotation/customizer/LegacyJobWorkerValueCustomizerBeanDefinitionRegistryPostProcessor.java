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
package io.camunda.spring.client.annotation.customizer;

import io.camunda.zeebe.spring.client.annotation.customizer.ZeebeWorkerValueCustomizer;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

public class LegacyJobWorkerValueCustomizerBeanDefinitionRegistryPostProcessor
    implements BeanDefinitionRegistryPostProcessor {

  private static final Logger LOG =
      LoggerFactory.getLogger(
          LegacyJobWorkerValueCustomizerBeanDefinitionRegistryPostProcessor.class);

  @Override
  public void postProcessBeanDefinitionRegistry(final BeanDefinitionRegistry registry)
      throws BeansException {
    final String[] beanDefinitionNames = registry.getBeanDefinitionNames();
    for (final String beanDefinitionName : beanDefinitionNames) {
      final BeanDefinition beanDefinition = registry.getBeanDefinition(beanDefinitionName);
      Optional.ofNullable(beanDefinition.getResolvableType().getRawClass())
          .ifPresent(
              beanClass -> {
                if (beanDefinition
                    .getResolvableType()
                    .getRawClass()
                    .isAssignableFrom(ZeebeWorkerValueCustomizer.class)) {
                  LOG.warn(
                      "Bean '{}' is implementing ZeebeWorkerValueCustomizer, please migrate to JobWorkerValueCustomizer",
                      beanDefinitionName);
                  final BeanDefinitionBuilder beanDefinitionBuilder =
                      BeanDefinitionBuilder.genericBeanDefinition(
                              JobWorkerValueCustomizerCompat.class)
                          .addConstructorArgReference(beanDefinitionName);
                  registry.registerBeanDefinition(
                      beanDefinitionName + "_Compat", beanDefinitionBuilder.getBeanDefinition());
                }
              });
    }
  }
}
