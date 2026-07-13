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
import io.camunda.client.bean.BeanInfo;
import io.camunda.client.lifecycle.CamundaClientLifecycleAware;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.ClassUtils;

public abstract class AbstractCamundaAnnotationProcessor
    implements ApplicationContextAware, CamundaClientLifecycleAware {
  private ApplicationContext applicationContext;

  @Override
  public void setApplicationContext(final ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  protected abstract boolean isApplicableFor(final BeanInfo beanInfo);

  protected abstract void configureFor(final BeanInfo beanInfo);

  /**
   * Starts the processor for a client. Subclasses override this (or the {@link
   * #start(CamundaClient, String)} overload). Defaults to a no-op.
   */
  protected void start(final CamundaClient client) {}

  /**
   * Stops the processor for a client. Subclasses override this (or the {@link #stop(CamundaClient,
   * String)} overload). Defaults to a no-op.
   */
  protected void stop(final CamundaClient client) {}

  /**
   * Starts the processor for a client, carrying its configured name in multi-client mode. Defaults
   * to the single-client {@link #start(CamundaClient)} for backwards compatibility.
   */
  protected void start(final CamundaClient client, final String clientName) {
    start(client);
  }

  /**
   * Stops the processor for a client, carrying its configured name in multi-client mode. Defaults
   * to the single-client {@link #stop(CamundaClient)} for backwards compatibility.
   */
  protected void stop(final CamundaClient client, final String clientName) {
    stop(client);
  }

  @Override
  public void onStart(final CamundaClient client) {
    onStart(client, null);
  }

  @Override
  public void onStart(final CamundaClient client, final String clientName) {
    for (final String beanName : applicationContext.getBeanDefinitionNames()) {
      final Class<?> beanType = applicationContext.getType(beanName, false);
      if (beanType != null) {
        final BeanInfo beanInfo =
            BeanInfo.builder()
                .beanName(beanName)
                // use Spring's ClassUtils to get the user class in case of a proxy
                .targetClass(ClassUtils.getUserClass(beanType))
                .beanSupplier(() -> applicationContext.getBean(beanName))
                .build();
        if (isApplicableFor(beanInfo)) {
          configureFor(beanInfo);
        }
      }
    }
    start(client, clientName);
  }

  @Override
  public void onStop(final CamundaClient client) {
    onStop(client, null);
  }

  @Override
  public void onStop(final CamundaClient client, final String clientName) {
    stop(client, clientName);
  }
}
