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
package io.camunda.zeebe.client.impl.response;

import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.MessagePublicationResult;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;

public final class PublishMessageResponseImpl implements PublishMessageResponse {

  private final long key;
  private final String tenantId;

  public PublishMessageResponseImpl(final GatewayOuterClass.PublishMessageResponse response) {
    key = response.getKey();
    tenantId = response.getTenantId();
  }

  public PublishMessageResponseImpl(final MessagePublicationResult response) {
    key = ParseUtil.parseLongOrEmpty(response.getMessageKey());
    tenantId = response.getTenantId();
  }

  @Override
  public long getMessageKey() {
    return key;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }
}
