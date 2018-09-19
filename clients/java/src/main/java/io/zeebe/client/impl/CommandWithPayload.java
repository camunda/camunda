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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Map;

public abstract class CommandWithPayload<T> {

  private final ObjectMapper jsonObjectMapper;

  public CommandWithPayload() {
    this.jsonObjectMapper = new ObjectMapper();
  }

  public T payload(final InputStream payload) {
    try {
      final String payloadString = jsonObjectMapper.readTree(payload).toString();
      return setPayloadInternal(payloadString);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public T payload(final String payload) {
    try {
      final String payloadString = jsonObjectMapper.readTree(payload).toString();
      return setPayloadInternal(payloadString);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public T payload(final Map<String, Object> payload) {
    return payload((Object) payload);
  }

  public T payload(final Object payload) {
    try {
      return setPayloadInternal(toJson(payload));
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  protected String toJson(final Object object) {
    try {
      return jsonObjectMapper.writeValueAsString(object);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  protected abstract T setPayloadInternal(String payload);
}
