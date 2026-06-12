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

import io.camunda.client.api.search.enums.WaitStateElementType;
import io.camunda.client.api.search.enums.WaitStateType;
import io.camunda.client.api.search.response.ElementInstanceWaitStateResult;
import io.camunda.client.api.search.response.WaitStateDetails;
import io.camunda.client.impl.util.EnumUtil;

public class ElementInstanceWaitStateResultImpl implements ElementInstanceWaitStateResult {

  private final WaitStateType waitStateType;
  private final String rootProcessInstanceKey;
  private final String processInstanceKey;
  private final String elementInstanceKey;
  private final String elementId;
  private final WaitStateElementType elementType;
  private final String tenantId;
  private final WaitStateDetails details;

  public ElementInstanceWaitStateResultImpl(
      final io.camunda.client.protocol.rest.ElementInstanceWaitStateResult item) {
    waitStateType =
        parseWaitStateType(item.getWaitStateType() != null ? item.getWaitStateType().name() : null);
    rootProcessInstanceKey = item.getRootProcessInstanceKey();
    processInstanceKey = item.getProcessInstanceKey();
    elementInstanceKey = item.getElementInstanceKey();
    elementId = item.getElementId();
    elementType = EnumUtil.convert(item.getElementType(), WaitStateElementType.class);
    tenantId = item.getTenantId();
    details = extractDetails(waitStateType, item);
  }

  private static WaitStateType parseWaitStateType(final String value) {
    if (value == null) {
      return WaitStateType.UNKNOWN_ENUM_VALUE;
    }
    try {
      return WaitStateType.valueOf(value);
    } catch (final IllegalArgumentException e) {
      return WaitStateType.UNKNOWN_ENUM_VALUE;
    }
  }

  private static WaitStateDetails extractDetails(
      final WaitStateType waitStateType,
      final io.camunda.client.protocol.rest.ElementInstanceWaitStateResult item) {
    switch (waitStateType) {
      case JOB:
        return item.getJobDetails() == null
            ? null
            : new JobWaitStateDetailsImpl(item.getJobDetails());
      case MESSAGE:
        return item.getMessageDetails() == null
            ? null
            : new MessageWaitStateDetailsImpl(item.getMessageDetails());
      case CONDITION:
        return item.getConditionDetails() == null
            ? null
            : new ConditionWaitStateDetailsImpl(item.getConditionDetails());
      default:
        return null;
    }
  }

  @Override
  public WaitStateType getWaitStateType() {
    return waitStateType;
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
  public WaitStateDetails getDetails() {
    return details;
  }
}
