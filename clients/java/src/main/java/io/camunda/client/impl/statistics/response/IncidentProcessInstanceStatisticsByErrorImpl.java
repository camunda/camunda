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

import io.camunda.client.api.statistics.response.IncidentProcessInstanceStatisticsByError;
import io.camunda.client.protocol.rest.IncidentProcessInstanceStatisticsByErrorResult;
import java.util.Objects;

public final class IncidentProcessInstanceStatisticsByErrorImpl
    implements IncidentProcessInstanceStatisticsByError {

  private final Integer errorHashCode;
  private final String errorMessage;
  private final Long activeInstancesWithErrorCount;

  public IncidentProcessInstanceStatisticsByErrorImpl(
      final IncidentProcessInstanceStatisticsByErrorResult result) {
    errorHashCode = result.getErrorHashCode();
    errorMessage = result.getErrorMessage();
    activeInstancesWithErrorCount = result.getActiveInstancesWithErrorCount();
  }

  @Override
  public Integer getErrorHashCode() {
    return errorHashCode;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public Long getActiveInstancesWithErrorCount() {
    return activeInstancesWithErrorCount;
  }

  @Override
  public int hashCode() {
    return Objects.hash(errorHashCode, errorMessage, activeInstancesWithErrorCount);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IncidentProcessInstanceStatisticsByErrorImpl that =
        (IncidentProcessInstanceStatisticsByErrorImpl) o;
    return Objects.equals(errorHashCode, that.errorHashCode)
        && Objects.equals(errorMessage, that.errorMessage)
        && Objects.equals(activeInstancesWithErrorCount, that.activeInstancesWithErrorCount);
  }

  @Override
  public String toString() {
    return "IncidentProcessInstanceStatisticsByErrorImpl{"
        + "errorHashCode='"
        + errorHashCode
        + '\''
        + ", errorMessage='"
        + errorMessage
        + '\''
        + ", activeInstancesWithErrorCount="
        + activeInstancesWithErrorCount
        + '}';
  }
}
