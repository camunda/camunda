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
package io.camunda.zeebe.spring.test.embedded;

import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.engine.EngineFactory;
import io.camunda.zeebe.spring.test.AbstractZeebeTestExecutionListener;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.test.util.TestSocketUtils;

/** Test execution listener binding the Zeebe engine to current test context. */
public class ZeebeTestExecutionListener extends AbstractZeebeTestExecutionListener
    implements TestExecutionListener, Ordered {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private ZeebeTestEngine zeebeEngine;

  @Override
  public void beforeTestMethod(@NonNull final TestContext testContext) {
    final int randomPort =
        TestSocketUtils
            .findAvailableTcpPort(); // can be replaced with TestSocketUtils once available:
    // https://github.com/spring-projects/spring-framework/pull/29132

    LOGGER.info("Create Zeebe in-memory engine for test run on random port: " + randomPort + "...");
    zeebeEngine = EngineFactory.create(randomPort);
    zeebeEngine.start();

    setupWithZeebeEngine(testContext, zeebeEngine);
  }

  @Override
  public void afterTestMethod(@NonNull final TestContext testContext) {
    cleanup(testContext, zeebeEngine);
    zeebeEngine.stop();
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
