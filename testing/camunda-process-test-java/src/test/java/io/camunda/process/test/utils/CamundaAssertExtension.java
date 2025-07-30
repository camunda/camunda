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
package io.camunda.process.test.utils;

import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaAssertAwaitBehavior;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

/**
 * A JUnit extension for assertion tests that replaces the {@link CamundaAssertAwaitBehavior}.
 *
 * <p>Annotate test methods that verify an assertion failure message with {@link
 * CamundaAssertExpectFailure} to reduce the assertion timeout and speed up the test case.
 */
public class CamundaAssertExtension implements BeforeEachCallback, AfterEachCallback {

  public static void expectFailure() {
    CamundaAssert.setAwaitBehavior(DevAwaitBehavior.expectFailure());
  }

  public static void expectSuccess() {
    CamundaAssert.setAwaitBehavior(DevAwaitBehavior.expectSuccess());
  }

  @Override
  public void beforeEach(final ExtensionContext context) {

    final boolean expectFailure =
        AnnotationSupport.isAnnotated(context.getElement(), CamundaAssertExpectFailure.class);

    if (expectFailure) {
      expectFailure();
    } else {
      expectSuccess();
    }
  }

  @Override
  public void afterEach(final ExtensionContext context) {
    CamundaAssert.setAwaitBehavior(CamundaAssert.DEFAULT_AWAIT_BEHAVIOR);
  }
}
