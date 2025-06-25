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
package io.camunda.process.test.util;

import io.camunda.process.test.impl.runtime.CamundaProcessTestGlobalRuntime;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlobalCptRuntimeInvalidator {
  private static final Logger LOG = LoggerFactory.getLogger(GlobalCptRuntimeInvalidator.class);

  private static final String GLOBAL_RUNTIME_RESET_METHOD = "resetRuntime";

  public static void invalidate() {
    try {
      final Method method =
          CamundaProcessTestGlobalRuntime.INSTANCE
              .getClass()
              .getDeclaredMethod(GLOBAL_RUNTIME_RESET_METHOD);
      method.setAccessible(true);
      method.invoke(CamundaProcessTestGlobalRuntime.INSTANCE);
    } catch (final Throwable t) {
      LOG.warn(
          "Unable to reset the CPT global runtime. Subsequent tests may behave erratically or fail.",
          t);
    }
  }
}
