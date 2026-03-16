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

import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.impl.proxy.CamundaProcessTestContextProxy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CamundaProcessTestContextProxyTest {

  @Mock private CamundaProcessTestContext camundaProcessTestContext;

  @Test
  void shouldThrowAssertionError() {
    // given
    final CamundaProcessTestContextProxy proxy = new CamundaProcessTestContextProxy();
    proxy.setDelegate(camundaProcessTestContext);

    final AssertionError assertionError = new AssertionError("expected");
    doThrow(assertionError).when(camundaProcessTestContext).completeUserTask("elementId");

    // when/then
    Assertions.assertThatThrownBy(
            () ->
                proxy.invoke(
                    proxy,
                    CamundaProcessTestContext.class.getMethod("completeUserTask", String.class),
                    new Object[] {"elementId"}))
        .isEqualTo(assertionError);
  }
}
