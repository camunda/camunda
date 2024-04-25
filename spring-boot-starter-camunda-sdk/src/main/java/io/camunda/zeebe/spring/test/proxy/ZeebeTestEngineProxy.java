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
package io.camunda.zeebe.spring.test.proxy;

import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import java.lang.reflect.Method;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Dynamic proxy to delegate to a {@link ZeebeTestEngine} which allows to swap the {@link
 * ZeebeTestEngine} object under the hood. This is used in test environments, where the while
 * ZeebeEngine is re-initialized for every test case
 */
public class ZeebeTestEngineProxy extends AbstractInvocationHandler {

  private ZeebeTestEngine delegate;

  public void swapZeebeEngine(ZeebeTestEngine client) {
    this.delegate = client;
  }

  public void removeZeebeEngine() {
    this.delegate = null;
  }

  @Override
  protected Object handleInvocation(Object proxy, Method method, @Nullable Object[] args)
      throws Throwable {
    if (delegate == null) {
      throw new RuntimeException(
          "Cannot invoke "
              + method
              + " on ZeebeTestEngine, as ZeebeTestEngine is currently not initialized. Maybe you run outside of a testcase?");
    }
    return method.invoke(delegate, args);
  }
}
