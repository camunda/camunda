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
package io.camunda.zeebe.spring.test;

import static io.camunda.zeebe.process.test.assertions.BpmnAssert.assertThat;

import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.process.test.filters.RecordStream;
import io.camunda.zeebe.process.test.inspections.model.InspectedProcessInstance;
import java.time.Duration;
import java.util.Objects;
import org.awaitility.Awaitility;

/** Helper to wait in the multithreaded environment for the worker to execute. */
public class ZeebeTestThreadSupport {

  private static final ThreadLocal<ZeebeTestEngine> ENGINES = new ThreadLocal<>();
  private static final Duration DEFAULT_DURATION = Duration.ofMillis(5000);
  private static final Integer DEFAULT_TIMES_PASSED = 1;
  private static final Long DEFAULT_INTERVAL_MILLIS = 100L;

  public static void setEngineForCurrentThread(final ZeebeTestEngine engine) {
    ENGINES.set(engine);
  }

  public static void cleanupEngineForCurrentThread() {
    ENGINES.remove();
  }

  public static void waitForProcessInstanceCompleted(final ProcessInstanceEvent processInstance) {
    waitForProcessInstanceCompleted(processInstance.getProcessInstanceKey(), DEFAULT_DURATION);
  }

  public static void waitForProcessInstanceCompleted(
      final ProcessInstanceEvent processInstance, final Duration duration) {
    waitForProcessInstanceCompleted(processInstance.getProcessInstanceKey(), duration);
  }

  public static void waitForProcessInstanceCompleted(final long processInstanceKey) {
    waitForProcessInstanceCompleted(
        new InspectedProcessInstance(processInstanceKey), DEFAULT_DURATION);
  }

  public static void waitForProcessInstanceCompleted(
      final long processInstanceKey, final Duration duration) {
    waitForProcessInstanceCompleted(new InspectedProcessInstance(processInstanceKey), duration);
  }

  public static void waitForProcessInstanceCompleted(
      final InspectedProcessInstance inspectedProcessInstance) {
    waitForProcessInstanceCompleted(inspectedProcessInstance, DEFAULT_DURATION);
  }

  public static void waitForProcessInstanceCompleted(
      final InspectedProcessInstance inspectedProcessInstance, Duration duration) {
    // get it in the thread of the test
    final ZeebeTestEngine engine = ENGINES.get();
    if (engine == null) {
      throw new IllegalStateException(
          "No Zeebe engine is initialized for the current thread, annotate the test with @ZeebeSpringTest");
    }
    if (duration == null) {
      duration = DEFAULT_DURATION;
    }
    Awaitility.await()
        .atMost(duration)
        .untilAsserted(
            () -> {
              // allow the worker to work
              Thread.sleep(DEFAULT_INTERVAL_MILLIS);
              BpmnAssert.initRecordStream(
                  RecordStream.of(Objects.requireNonNull(engine).getRecordStreamSource()));
              // use inside the awaitility thread
              assertThat(inspectedProcessInstance).isCompleted();
            });
  }

  public static void waitForProcessInstanceHasPassedElement(
      final ProcessInstanceEvent processInstance, final String elementId) {
    waitForProcessInstanceHasPassedElement(
        processInstance.getProcessInstanceKey(), elementId, DEFAULT_DURATION);
  }

  public static void waitForProcessInstanceHasPassedElement(
      final ProcessInstanceEvent processInstance, final String elementId, final Duration duration) {
    waitForProcessInstanceHasPassedElement(
        processInstance.getProcessInstanceKey(), elementId, duration);
  }

  public static void waitForProcessInstanceHasPassedElement(
      final long processInstanceKey, final String elementId) {
    waitForProcessInstanceHasPassedElement(
        new InspectedProcessInstance(processInstanceKey), elementId, DEFAULT_DURATION);
  }

  public static void waitForProcessInstanceHasPassedElement(
      final long processInstanceKey, final String elementId, final Duration duration) {
    waitForProcessInstanceHasPassedElement(
        new InspectedProcessInstance(processInstanceKey), elementId, duration);
  }

  public static void waitForProcessInstanceHasPassedElement(
      final InspectedProcessInstance inspectedProcessInstance, final String elementId) {
    waitForProcessInstanceHasPassedElement(inspectedProcessInstance, elementId, DEFAULT_DURATION);
  }

  public static void waitForProcessInstanceHasPassedElement(
      final InspectedProcessInstance inspectedProcessInstance,
      final String elementId,
      final Duration duration) {
    waitForProcessInstanceHasPassedElement(
        inspectedProcessInstance, elementId, duration, DEFAULT_TIMES_PASSED);
  }

  public static void waitForProcessInstanceHasPassedElement(
      final InspectedProcessInstance inspectedProcessInstance,
      final String elementId,
      Duration duration,
      final int times) {
    final ZeebeTestEngine engine = ENGINES.get();
    if (engine == null) {
      throw new IllegalStateException(
          "No Zeebe engine is initialized for the current thread, annotate the test with @ZeebeSpringTest");
    }
    if (duration == null) {
      duration = DEFAULT_DURATION;
    }
    Awaitility.await()
        .atMost(duration)
        .untilAsserted(
            () -> {
              Thread.sleep(DEFAULT_INTERVAL_MILLIS);
              BpmnAssert.initRecordStream(
                  RecordStream.of(Objects.requireNonNull(engine).getRecordStreamSource()));
              assertThat(inspectedProcessInstance).hasPassedElement(elementId, times);
            });
  }
}
