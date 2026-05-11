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
package io.camunda.client.impl.search.response;

import io.camunda.client.api.search.response.WaitStateDetails;
import java.util.Objects;

public final class WaitStateDetailsImpl implements WaitStateDetails {

  private final String jobKey;
  private final String jobType;
  private final String jobKind;

  public WaitStateDetailsImpl(final io.camunda.client.protocol.rest.WaitStateDetails item) {
    jobKey = item.getJobKey();
    jobType = item.getJobType();
    jobKind = item.getJobKind();
  }

  @Override
  public String getJobKey() {
    return jobKey;
  }

  @Override
  public String getJobType() {
    return jobType;
  }

  @Override
  public String getJobKind() {
    return jobKind;
  }

  @Override
  public int hashCode() {
    return Objects.hash(jobKey, jobType, jobKind);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final WaitStateDetailsImpl that = (WaitStateDetailsImpl) o;
    return Objects.equals(jobKey, that.jobKey)
        && Objects.equals(jobType, that.jobType)
        && Objects.equals(jobKind, that.jobKind);
  }
}
