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

import io.camunda.client.api.statistics.response.JobErrorStatisticsItem;
import java.util.Objects;

public class JobErrorStatisticsItemImpl implements JobErrorStatisticsItem {

  private final String errorCode;
  private final String errorMessage;
  private final int workers;

  public JobErrorStatisticsItemImpl(
      final String errorCode, final String errorMessage, final int workers) {
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.workers = workers;
  }

  @Override
  public String getErrorCode() {
    return errorCode;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public int getWorkers() {
    return workers;
  }

  @Override
  public int hashCode() {
    return Objects.hash(errorCode, errorMessage, workers);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JobErrorStatisticsItemImpl that = (JobErrorStatisticsItemImpl) o;
    return workers == that.workers
        && Objects.equals(errorCode, that.errorCode)
        && Objects.equals(errorMessage, that.errorMessage);
  }

  @Override
  public String toString() {
    return "JobErrorStatisticsItemImpl{"
        + "errorCode='"
        + errorCode
        + '\''
        + ", errorMessage='"
        + errorMessage
        + '\''
        + ", workers="
        + workers
        + '}';
  }
}
