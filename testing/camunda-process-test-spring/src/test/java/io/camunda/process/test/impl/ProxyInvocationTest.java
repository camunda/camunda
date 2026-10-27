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
package io.camunda.process.test.impl;

import static org.mockito.Mockito.doThrow;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.testCases.TestCase;
import io.camunda.process.test.api.testCases.TestCaseRunner;
import io.camunda.process.test.impl.proxy.AbstractInvocationHandler;
import io.camunda.process.test.impl.proxy.CamundaClientProxy;
import io.camunda.process.test.impl.proxy.CamundaProcessTestContextProxy;
import io.camunda.process.test.impl.proxy.TestCaseRunnerProxy;
import java.lang.reflect.Proxy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProxyInvocationTest {

  private static <I extends AbstractInvocationHandler<D>, D> D createProxy(
      final I invocationHandler, final Class<D> delegateClass) {
    return (D)
        Proxy.newProxyInstance(
            invocationHandler.getClass().getClassLoader(),
            new Class<?>[] {delegateClass},
            invocationHandler);
  }

  @Nested
  class TestCaseRunnerTests {

    @Mock private TestCase testCase;
    @Mock private TestCaseRunner testCaseRunner;

    @Test
    void shouldThrowAssertionError() {
      // given
      final TestCaseRunnerProxy invocationHandler = new TestCaseRunnerProxy();
      invocationHandler.setDelegate(testCaseRunner);

      final AssertionError assertionError = new AssertionError("expected");
      doThrow(assertionError).when(testCaseRunner).run(testCase);

      final TestCaseRunner proxy = createProxy(invocationHandler, TestCaseRunner.class);

      // when/then
      Assertions.assertThatThrownBy(() -> proxy.run(testCase)).isEqualTo(assertionError);
    }
  }

  @Nested
  class CamundaProcessTestContextTests {

    @Mock private CamundaProcessTestContext camundaProcessTestContext;

    @Test
    void shouldThrowAssertionError() {
      // given
      final CamundaProcessTestContextProxy invocationHandler = new CamundaProcessTestContextProxy();
      invocationHandler.setDelegate(camundaProcessTestContext);

      final AssertionError assertionError = new AssertionError("expected");
      doThrow(assertionError).when(camundaProcessTestContext).completeUserTask("elementId");

      final CamundaProcessTestContext proxy =
          createProxy(invocationHandler, CamundaProcessTestContext.class);

      // when/then
      Assertions.assertThatThrownBy(() -> proxy.completeUserTask("elementId"))
          .isEqualTo(assertionError);
    }
  }

  @Nested
  class CamundaClientTests {

    @Mock private CamundaClient camundaClient;

    @Test
    void shouldThrowClientException() {
      // given
      final CamundaClientProxy invocationHandler = new CamundaClientProxy();
      invocationHandler.setDelegate(camundaClient);

      final ClientException clientException = new ClientException("expected");
      doThrow(clientException).when(camundaClient).close();

      final CamundaClient proxy = createProxy(invocationHandler, CamundaClient.class);

      // when/then
      Assertions.assertThatThrownBy(proxy::close).isEqualTo(clientException);
    }
  }
}
