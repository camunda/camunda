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

import io.camunda.process.test.api.dsl.TestScenarioRunner;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Dynamic proxy to delegate to a {@link TestScenarioRunner} which allows to swap the object under
 * the hood.
 */
public class TestScenarioRunnerProxy extends AbstractInvocationHandler {

  private TestScenarioRunner delegate;

  public void setRunner(final TestScenarioRunner testScenarioRunner) {
    delegate = testScenarioRunner;
  }

  public void removeRunner() {
    delegate = null;
  }

  @Override
  protected Object handleInvocation(
      final Object proxy, final Method method, @Nullable final Object[] args) throws Throwable {
    if (delegate == null) {
      throw new RuntimeException(
          "Cannot invoke "
              + method
              + " on TestScenarioRunner, as TestScenarioRunner is currently not initialized. Maybe you run outside of a testcase?");
    }
    try {
      return method.invoke(delegate, args);

    } catch (final InvocationTargetException e) {
      if (e.getCause() instanceof final AssertionError assertionError) {
        // unwrap assertion errors to make them visible to the test framework
        throw assertionError;
      } else {
        throw e;
      }
    }
  }
}
