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
   * Called to start the processor with the given client.
   *
   * @param client the CamundaClient
   * @deprecated Override {@link #start(CamundaClient, String)} instead
   */
  protected void start(final CamundaClient client) {
    // Default empty implementation for backwards compatibility
  }

  /**
   * Called to start the processor with the given client.
   *
   * @param client the CamundaClient
   * @param clientName the name of the client, or null in single-client mode
   */
  protected void start(final CamundaClient client, final String clientName) {
    // Default implementation calls the legacy method for backwards compatibility
    start(client);
  }

  /**
   * Called to stop the processor with the given client.
   *
   * @param client the CamundaClient
   * @deprecated Override {@link #stop(CamundaClient, String)} instead
   */
  protected void stop(final CamundaClient client) {
    // Default empty implementation for backwards compatibility
  }

  /**
   * Called to stop the processor with the given client.
   *
   * @param client the CamundaClient
   * @param clientName the name of the client, or null in single-client mode
   */
  protected void stop(final CamundaClient client, final String clientName) {
    // Default implementation calls the legacy method for backwards compatibility
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
