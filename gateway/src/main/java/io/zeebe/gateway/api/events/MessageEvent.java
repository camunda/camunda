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
package io.zeebe.gateway.api.events;

import io.zeebe.gateway.api.record.Record;
import java.time.Duration;
import java.util.Map;

public interface MessageEvent extends Record {

  /** @return the current state */
  MessageState getState();

  /** @return the name of the message. */
  String getName();

  /** @return the correlation-key of the message. */
  String getCorrelationKey();

  /** @return the id of the message, or <code>null</code> if not set. */
  String getMessageId();

  /** @return the time-to-live of the message. */
  Duration getTimeToLive();

  /** @return the payload of the message as JSON-formatted string. */
  String getPayload();

  /** @return the de-serialized payload of the message as map. */
  Map<String, Object> getPayloadAsMap();

  /** @return de-serialized payload of the message as the given type. */
  <T> T getPayloadAsType(Class<T> payloadType);
}
