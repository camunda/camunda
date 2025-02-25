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
package io.camunda.process.test.impl.proxy;

import java.lang.reflect.Method;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class AbstractClientProxy<T> extends AbstractInvocationHandler {

  private T delegate;

  public void setClient(final T client) {
    delegate = client;
  }

  public void removeClient() {
    delegate = null;
  }

  @Override
  protected Object handleInvocation(
      final Object proxy, final Method method, @Nullable final Object[] args) throws Throwable {
    if (delegate == null) {
      final String delegateClassName = getDelegateClass().getSimpleName();
      throw new RuntimeException(
          "Cannot invoke %s on %s, as %s is currently not initialized. Maybe you run outside of a testcase?"
              .formatted(method, delegateClassName, delegateClassName));
    }
    return method.invoke(delegate, args);
  }

  protected abstract Class<T> getDelegateClass();
}
