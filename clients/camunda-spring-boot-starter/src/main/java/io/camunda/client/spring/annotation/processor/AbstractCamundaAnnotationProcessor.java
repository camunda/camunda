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

import io.camunda.client.CamundaClient;
import io.camunda.client.lifecycle.CamundaClientLifecycleAware;
import io.camunda.client.spring.bean.ClassInfo;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public abstract class AbstractCamundaAnnotationProcessor
    implements ApplicationContextAware, CamundaClientLifecycleAware {
  private ApplicationContext applicationContext;

  @Override
  public void setApplicationContext(final ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  protected abstract boolean isApplicableFor(ClassInfo beanInfo);

  protected abstract void configureFor(final ClassInfo beanInfo);

  protected abstract void start(CamundaClient client);

  protected abstract void stop(CamundaClient client);

  @Override
  public void onStart(final CamundaClient client) {
    for (final String beanName : applicationContext.getBeanDefinitionNames()) {
      final Object bean = applicationContext.getBean(beanName);
      final ClassInfo classInfo = ClassInfo.builder().beanName(beanName).bean(bean).build();
      if (isApplicableFor(classInfo)) {
        configureFor(classInfo);
      }
    }
    start(client);
  }

  @Override
  public void onStop(final CamundaClient client) {
    stop(client);
  }
}
