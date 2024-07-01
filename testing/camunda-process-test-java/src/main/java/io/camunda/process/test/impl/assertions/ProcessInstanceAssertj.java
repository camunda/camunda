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
package io.camunda.process.test.impl.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.impl.client.CamundaClientNotFoundException;
import io.camunda.process.test.impl.client.ProcessInstanceDto;
import io.camunda.process.test.impl.client.ProcessInstanceState;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.awaitility.core.TerminalFailureException;

public class ProcessInstanceAssertj extends AbstractAssert<ProcessInstanceAssertj, Long>
    implements ProcessInstanceAssert {

  private final CamundaDataSource dataSource;

  public ProcessInstanceAssertj(final CamundaDataSource dataSource, final long processInstanceKey) {
    super(processInstanceKey, ProcessInstanceAssertj.class);
    this.dataSource = dataSource;
  }

  @Override
  public ProcessInstanceAssert isActive() {
    hasProcessInstanceInState(ProcessInstanceState.ACTIVE, Objects::nonNull);
    return this;
  }

  @Override
  public ProcessInstanceAssert isCompleted() {
    hasProcessInstanceInState(ProcessInstanceState.COMPLETED, ProcessInstanceAssertj::isEnded);
    return this;
  }

  @Override
  public ProcessInstanceAssert isTerminated() {
    hasProcessInstanceInState(ProcessInstanceState.TERMINATED, ProcessInstanceAssertj::isEnded);
    return this;
  }

  private void hasProcessInstanceInState(
      final ProcessInstanceState expectedState, final Predicate<ProcessInstanceDto> waitCondition) {

    final AtomicReference<ProcessInstanceDto> reference = new AtomicReference<>();

    try {
      Awaitility.await()
          .ignoreException(CamundaClientNotFoundException.class)
          .failFast(() -> waitCondition.test(reference.get()))
          .untilAsserted(
              () -> {
                final ProcessInstanceDto processInstance = dataSource.getProcessInstance(actual);
                reference.set(processInstance);

                assertThat(processInstance.getState()).isEqualTo(expectedState);
              });

    } catch (final ConditionTimeoutException | TerminalFailureException e) {

      final String actualState =
          Optional.ofNullable(reference.get())
              .map(ProcessInstanceDto::getState)
              .map(ProcessInstanceAssertj::formatState)
              .orElse("not activated");

      final String failureMessage =
          String.format(
              "%s should be %s but was %s.",
              formatProcessInstance(), formatState(expectedState), actualState);
      fail(failureMessage);
    }
  }

  private static boolean isEnded(final ProcessInstanceDto processInstance) {
    return processInstance != null && processInstance.getEndDate() != null;
  }

  private String formatProcessInstance() {
    return String.format("Process instance [key: %s]", actual);
  }

  private static String formatState(final ProcessInstanceState state) {
    if (state == null) {
      return "not activated";
    } else {
      return state.name().toLowerCase();
    }
  }
}
