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

import io.camunda.client.api.search.enums.TimerState;
import io.camunda.client.api.search.response.Timer;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.TimerResult;
import java.time.OffsetDateTime;

public class TimerImpl implements Timer {

  private final Long timerKey;
  private final String processDefinitionId;
  private final Long processInstanceKey;
  private final String elementId;
  private final Long elementInstanceKey;
  private final TimerState state;
  private final OffsetDateTime dueDate;
  private final Integer repetitions;
  private final String tenantId;

  public TimerImpl(final TimerResult item) {
    timerKey = ParseUtil.parseLongOrNull(item.getTimerKey());
    processDefinitionId = item.getProcessDefinitionId();
    processInstanceKey = ParseUtil.parseLongOrNull(item.getProcessInstanceKey());
    elementId = item.getElementId();
    elementInstanceKey = ParseUtil.parseLongOrNull(item.getElementInstanceKey());
    state = EnumUtil.convert(item.getState(), TimerState.class);
    dueDate = ParseUtil.parseOffsetDateTimeOrNull(item.getDueDate());
    repetitions = item.getRepetitions();
    tenantId = item.getTenantId();
  }

  @Override
  public Long getTimerKey() {
    return timerKey;
  }

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  @Override
  public Long getProcessInstanceKey() {
    return processInstanceKey;
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
  public TimerState getState() {
    return state;
  }

  @Override
  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  @Override
  public Integer getRepetitions() {
    return repetitions;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }
}
