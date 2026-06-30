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
package io.camunda.client.impl.statistics.response;

import io.camunda.client.api.statistics.response.ProcessInstanceWaitStateStatistics;
import io.camunda.client.protocol.rest.ProcessInstanceWaitStateStatisticsResult;
import java.util.Objects;

public class ProcessInstanceWaitStateStatisticsImpl implements ProcessInstanceWaitStateStatistics {

  private final String elementId;
  private final Long waitingCount;

  public ProcessInstanceWaitStateStatisticsImpl(
      final ProcessInstanceWaitStateStatisticsResult statistics) {
    elementId = statistics.getElementId();
    waitingCount = statistics.getWaitingCount();
  }

  public ProcessInstanceWaitStateStatisticsImpl(final String elementId, final Long waitingCount) {
    this.elementId = elementId;
    this.waitingCount = waitingCount;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public Long getWaitingCount() {
    return waitingCount;
  }

  @Override
  public int hashCode() {
    return Objects.hash(elementId, waitingCount);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProcessInstanceWaitStateStatisticsImpl that = (ProcessInstanceWaitStateStatisticsImpl) o;
    return Objects.equals(elementId, that.elementId)
        && Objects.equals(waitingCount, that.waitingCount);
  }

  @Override
  public String toString() {
    return String.format(
        "ProcessInstanceWaitStateStatisticsImpl{elementId='%s', waitingCount=%s}",
        elementId, waitingCount);
  }
}
