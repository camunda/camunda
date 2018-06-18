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
package io.zeebe.test.broker.protocol.clientapi;

import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.test.util.collection.MapBuilder;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.RemoteAddress;
import java.util.Map;

public class ControlMessageRequestBuilder {
  protected ControlMessageRequest request;

  public ControlMessageRequestBuilder(
      ClientOutput output, RemoteAddress target, MsgPackHelper msgPackHelper) {
    request = new ControlMessageRequest(output, target, msgPackHelper);
  }

  public ControlMessageRequest send() {
    return request.send();
  }

  public ControlMessageRequest sendWithoutRetries() {
    return request.send(b -> false);
  }

  public ControlMessageResponse sendAndAwait() {
    return send().await();
  }

  public ControlMessageRequestBuilder messageType(ControlMessageType msgType) {
    request.messageType(msgType);
    return this;
  }

  public ControlMessageRequestBuilder partitionId(int partitionId) {
    request.partitionId(partitionId);
    return this;
  }

  public ControlMessageRequestBuilder data(Map<String, Object> data) {
    request.data(data);
    return this;
  }

  public MapBuilder<ControlMessageRequestBuilder> data() {
    return new MapBuilder<>(this, this::data);
  }
}
