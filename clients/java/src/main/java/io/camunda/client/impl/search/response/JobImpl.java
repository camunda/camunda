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
import io.camunda.client.api.search.enums.JobState;
import io.camunda.client.api.search.enums.ListenerEventType;
import io.camunda.client.api.search.response.Job;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.JobSearchResult;
import java.time.OffsetDateTime;
import java.util.Map;

public class JobImpl implements Job {

  private final Long jobKey;
  private final String type;
  private final String worker;
  private final JobState state;
  private final JobKind kind;
  private final ListenerEventType listenerEventType;
  private final Integer retries;
  private final Boolean isDenied;
  private final String deniedReason;
  private final Boolean hasFailedWithRetriesLeft;
  private final String errorCode;
  private final String errorMessage;
  private final Map<String, String> customerHeaders;
  private final OffsetDateTime deadline;
  private final OffsetDateTime endTime;
  private final String processDefinitionId;
  private final Long processDefinitionKey;
  private final Long processInstanceKey;
  private final Long rootProcessInstanceKey;
  private final String elementId;
  private final Long elementInstanceKey;
  private final String tenantId;
  private final OffsetDateTime creationTime;
  private final OffsetDateTime lastUpdatedTime;

  public JobImpl(final JobSearchResult item) {
    jobKey = ParseUtil.parseLongOrNull(item.getJobKey());
    type = item.getType();
    worker = item.getWorker();
    state = EnumUtil.convert(item.getState(), JobState.class);
    kind = EnumUtil.convert(item.getKind(), JobKind.class);
    listenerEventType = EnumUtil.convert(item.getListenerEventType(), ListenerEventType.class);
    retries = item.getRetries();
    isDenied = item.getIsDenied();
    deniedReason = item.getDeniedReason();
    hasFailedWithRetriesLeft = item.getHasFailedWithRetriesLeft();
    errorCode = item.getErrorCode();
    errorMessage = item.getErrorMessage();
    customerHeaders = item.getCustomHeaders();
    deadline = ParseUtil.parseOffsetDateTimeOrNull(item.getDeadline());
    endTime = ParseUtil.parseOffsetDateTimeOrNull(item.getEndTime());
    processDefinitionId = item.getProcessDefinitionId();
    processDefinitionKey = ParseUtil.parseLongOrNull(item.getProcessDefinitionKey());
    processInstanceKey = ParseUtil.parseLongOrNull(item.getProcessInstanceKey());
    rootProcessInstanceKey = ParseUtil.parseLongOrNull(item.getRootProcessInstanceKey());
    elementId = item.getElementId();
    elementInstanceKey = ParseUtil.parseLongOrNull(item.getElementInstanceKey());
    tenantId = item.getTenantId();
    creationTime = ParseUtil.parseOffsetDateTimeOrNull(item.getCreationTime());
    lastUpdatedTime = ParseUtil.parseOffsetDateTimeOrNull(item.getLastUpdateTime());
  }

  @Override
  public Long getJobKey() {
    return jobKey;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public String getWorker() {
    return worker;
  }

  @Override
  public JobState getState() {
    return state;
  }

  @Override
  public JobKind getKind() {
    return kind;
  }

  @Override
  public ListenerEventType getListenerEventType() {
    return listenerEventType;
  }

  @Override
  public Integer getRetries() {
    return retries;
  }

  @Override
  public Boolean isDenied() {
    return isDenied;
  }

  @Override
  public String getDeniedReason() {
    return deniedReason;
  }

  @Override
  public Boolean hasFailedWithRetriesLeft() {
    return hasFailedWithRetriesLeft;
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
  public Map<String, String> getCustomerHeaders() {
    return customerHeaders;
  }

  @Override
  public OffsetDateTime getDeadline() {
    return deadline;
  }

  @Override
  public OffsetDateTime getEndTime() {
    return endTime;
  }

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  @Override
  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public Long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  @Override
  public OffsetDateTime getLastUpdateTime() {
    return lastUpdatedTime;
  }
}
