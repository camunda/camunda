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
package io.camunda.client.bean;

import java.lang.reflect.Method;

public final class MethodInfoBuilder {
  private BeanInfo beanInfo;
  private Method method;

  public MethodInfoBuilder beanInfo(final BeanInfo beanInfo) {
    this.beanInfo = beanInfo;
    return this;
  }

  public MethodInfoBuilder method(final Method method) {
    this.method = method;
    return this;
  }

  public MethodInfo build() {
    assert method != null : "method is null";
    assert beanInfo != null : "beanInfo is null";
    return InfoFactory.instance().methodInfo(beanInfo, method);
  }
}
