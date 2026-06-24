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
package io.camunda.process.test.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.utils.ProcessInstanceBuilder;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CamundaAssertTest {

  @Mock private CamundaDataSource camundaDataSource;

  @Mock private ProcessInstanceEvent processInstanceEvent;

  @Test
  void shouldFailIfNotInitialized() {
    // given
    CamundaAssert.reset();

    // when/then
    assertThatThrownBy(
            () -> CamundaAssert.assertThatProcessInstance(processInstanceEvent).isActive())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No data source is set. Maybe you run outside of a testcase?");
  }

  @Test
  void shouldUseDataSource() {
    // given
    final long processInstanceKey = 100L;
    when(processInstanceEvent.getProcessInstanceKey()).thenReturn(processInstanceKey);

    when(camundaDataSource.findProcessInstances(any()))
        .thenReturn(
            Collections.singletonList(
                ProcessInstanceBuilder.newActiveProcessInstance(processInstanceKey).build()));

    // when
    CamundaAssert.initialize(camundaDataSource);
    CamundaAssert.assertThatProcessInstance(processInstanceEvent).isActive();

    // then
    verify(camundaDataSource).findProcessInstances(any());
  }
}
