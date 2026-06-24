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
package io.camunda.client.impl.response;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.response.CorrelateMessageResponse;
import io.camunda.client.protocol.rest.MessageCorrelationResult;

public final class CorrelateMessageResponseImpl implements CorrelateMessageResponse {

  private final JsonMapper jsonMapper;
  private long key;
  private String tenantId;
  private long processInstanceKey;

  public CorrelateMessageResponseImpl(final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Long getMessageKey() {
    return key;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public CorrelateMessageResponseImpl setResponse(final MessageCorrelationResult response) {
    key = Long.parseLong(response.getMessageKey());
    tenantId = response.getTenantId();
    processInstanceKey = Long.parseLong(response.getProcessInstanceKey());
    return this;
  }
}
