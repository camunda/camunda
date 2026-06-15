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

import static io.camunda.client.protocol.rest.WaitStateTypeEnum.UNKNOWN_DEFAULT_OPEN_API;

import io.camunda.client.api.search.enums.WaitStateElementType;
import io.camunda.client.api.search.enums.WaitStateType;
import io.camunda.client.api.search.response.ElementInstanceWaitStateResult;
import io.camunda.client.api.search.response.WaitStateDetails;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.WaitStateTypeEnum;

public class ElementInstanceWaitStateResultImpl implements ElementInstanceWaitStateResult {

  private final String rootProcessInstanceKey;
  private final String processInstanceKey;
  private final String elementInstanceKey;
  private final String elementId;
  private final WaitStateElementType elementType;
  private final String tenantId;
  private final String bpmnProcessId;
  private final WaitStateDetails details;

  public ElementInstanceWaitStateResultImpl(
      final io.camunda.client.protocol.rest.ElementInstanceWaitStateResult item) {
    rootProcessInstanceKey = item.getRootProcessInstanceKey();
    processInstanceKey = item.getProcessInstanceKey();
    elementInstanceKey = item.getElementInstanceKey();
    elementId = item.getElementId();
    elementType = EnumUtil.convert(item.getElementType(), WaitStateElementType.class);
    tenantId = item.getTenantId();
    bpmnProcessId = item.getBpmnProcessId();
    details = extractDetails(item.getDetails());
  }

  private static WaitStateDetails extractDetails(
      final io.camunda.client.protocol.rest.WaitStateDetails item) {

    final WaitStateTypeEnum waitStateType =
        (item != null && item.getWaitStateType() != null)
            ? WaitStateTypeEnum.fromValue(item.getWaitStateType())
            : UNKNOWN_DEFAULT_OPEN_API;
    switch (waitStateType) {
      case JOB:
        return new JobWaitStateDetailsImpl(
            (io.camunda.client.protocol.rest.JobWaitStateDetails) item);
      case MESSAGE:
        return new MessageWaitStateDetailsImpl(
            (io.camunda.client.protocol.rest.MessageWaitStateDetails) item);
      case TIMER:
        return new TimerWaitStateDetailsImpl(
            (io.camunda.client.protocol.rest.TimerWaitStateDetails) item);
      case USER_TASK:
        return new UserTaskWaitStateDetailsImpl(
            (io.camunda.client.protocol.rest.UserTaskWaitStateDetails) item);
      default:
        return null;
    }
  }

  @Override
  public WaitStateType getWaitStateType() {
    return details.getWaitStateType();
  }

  @Override
  public String getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  @Override
  public String getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public String getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public WaitStateElementType getElementType() {
    return elementType;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public WaitStateDetails getDetails() {
    return details;
  }
}
