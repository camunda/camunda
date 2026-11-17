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

import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.enums.ListenerEventType;
import io.camunda.client.api.search.enums.WaitStateType;
import io.camunda.client.api.search.response.JobWaitStateDetails;
import io.camunda.client.impl.util.EnumUtil;

public class JobWaitStateDetailsImpl implements JobWaitStateDetails {

  private final String jobKey;
  private final String jobType;
  private final JobKind jobKind;
  private final ListenerEventType listenerEventType;
  private final Integer retries;

  public JobWaitStateDetailsImpl(
      final io.camunda.client.protocol.rest.JobWaitStateDetails details) {
    jobKey = details.getJobKey();
    jobType = details.getJobType();
    jobKind = EnumUtil.convert(details.getJobKind(), JobKind.class);
    listenerEventType = EnumUtil.convert(details.getListenerEventType(), ListenerEventType.class);
    retries = details.getRetries();
  }

  @Override
  public WaitStateType getWaitStateType() {
    return WaitStateType.JOB;
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
  public JobKind getJobKind() {
    return jobKind;
  }

  @Override
  public ListenerEventType getListenerEventType() {
    return listenerEventType;
  }

  @Override
  public Integer getRetries() {
    return retries;
  }
}
