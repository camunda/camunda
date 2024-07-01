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

import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.assertions.ProcessInstanceAssertj;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import java.time.Duration;
import org.awaitility.Awaitility;

public class BpmnAssert {

  public static final Duration DEFAULT_ASSERTION_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration DEFAULT_ASSERTION_INTERVAL = Duration.ofMillis(100);

  private static final ThreadLocal<CamundaDataSource> DATA_SOURCE = new ThreadLocal<>();

  static {
    Awaitility.setDefaultTimeout(DEFAULT_ASSERTION_TIMEOUT);
    Awaitility.setDefaultPollInterval(DEFAULT_ASSERTION_INTERVAL);
  }

  // ======== Configuration options ========

  public static void setAssertionTimeout(final Duration assertionTimeout) {
    Awaitility.setDefaultTimeout(assertionTimeout);
  }

  public static void setAssertionInterval(final Duration assertionInterval) {
    Awaitility.setDefaultPollInterval(assertionInterval);
  }

  // ======== Assertions ========

  public static ProcessInstanceAssert assertThat(final ProcessInstanceEvent processInstanceEvent) {
    return new ProcessInstanceAssertj(
        getDataSource(), processInstanceEvent.getProcessInstanceKey());
  }

  public static ProcessInstanceAssert assertThat(
      final ProcessInstanceResult processInstanceResult) {
    return new ProcessInstanceAssertj(
        getDataSource(), processInstanceResult.getProcessInstanceKey());
  }

  // ======== Internal ========

  private static CamundaDataSource getDataSource() {
    if (DATA_SOURCE.get() == null) {
      throw new IllegalStateException(
          "No data source is set. Maybe you run outside of a testcase?");
    }
    return DATA_SOURCE.get();
  }

  /**
   * Initializes the assertions by setting the data source. Must be called before the assertions are
   * used.
   */
  static void initialize(final CamundaDataSource dataSource) {
    BpmnAssert.DATA_SOURCE.set(dataSource);
  }

  /**
   * Resets the assertions by removing the data source. Must call {@link
   * #initialize(CamundaDataSource)} before the assertions can be used again.
   */
  static void reset() {
    BpmnAssert.DATA_SOURCE.remove();
  }
}
