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
package io.zeebe.client.impl;

import java.io.InputStream;
import java.util.Map;

public abstract class CommandWithPayload<T> {

  protected final ZeebeObjectMapper objectMapper;

  public CommandWithPayload(ZeebeObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public T payload(final InputStream payload) {
    ArgumentUtil.ensureNotNull("payload", payload);
    return setPayloadInternal(objectMapper.validateJson("payload", payload));
  }

  public T payload(final String payload) {
    ArgumentUtil.ensureNotNull("payload", payload);
    return setPayloadInternal(objectMapper.validateJson("payload", payload));
  }

  public T payload(final Map<String, Object> payload) {
    ArgumentUtil.ensureNotNull("payload", payload);
    return payload((Object) payload);
  }

  public T payload(final Object payload) {
    ArgumentUtil.ensureNotNull("payload", payload);
    return setPayloadInternal(objectMapper.toJson(payload));
  }

  protected abstract T setPayloadInternal(String payload);
}
