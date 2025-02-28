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
package io.camunda.zeebe.spring.client.bean;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

public class CopyWithProtectionBeanUtilsBean extends CopyNotNullBeanUtilsBean {
  private final Set<String> protectedProperties;

  public CopyWithProtectionBeanUtilsBean(final Set<String> protectedProperties) {
    this.protectedProperties = protectedProperties;
  }

  @Override
  public void copyProperty(final Object bean, final String name, final Object value)
      throws IllegalAccessException, InvocationTargetException {
    if (!protectedProperties.contains(name)) {
      super.copyProperty(bean, name, value);
    }
  }
}
