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

import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.test.util.collection.MapBuilder;
import io.zeebe.transport.ClientOutput;
import io.zeebe.util.buffer.BufferWriter;
import java.util.Map;

public class ExecuteCommandRequestBuilder {
  protected ExecuteCommandRequest request;

  public ExecuteCommandRequestBuilder(
      ClientOutput output, int target, MsgPackHelper msgPackHelper) {
    this.request = new ExecuteCommandRequest(output, target, msgPackHelper);
  }

  public ExecuteCommandResponse sendAndAwait() {
    return send().await();
  }

  public ExecuteCommandRequest send() {
    return request.send();
  }

  public ExecuteCommandRequest sendWithoutRetries() {
    return request.send(r -> false);
  }

  public ExecuteCommandRequestBuilder partitionId(int partitionId) {
    request.partitionId(partitionId);
    return this;
  }

  public ExecuteCommandRequestBuilder key(long key) {
    request.key(key);
    return this;
  }

  public ExecuteCommandRequestBuilder type(ValueType valueType, Intent intent) {
    request.valueType(valueType);
    request.intent(intent);
    return this;
  }

  public ExecuteCommandRequestBuilder intent(Intent intent) {
    request.intent(intent);
    return this;
  }

  public ExecuteCommandRequestBuilder command(Map<String, Object> command) {
    request.command(command);
    return this;
  }

  public ExecuteCommandRequestBuilder command(BufferWriter command) {
    request.command(command);
    return this;
  }

  public MapBuilder<ExecuteCommandRequestBuilder> command() {
    final MapBuilder<ExecuteCommandRequestBuilder> mapBuilder =
        new MapBuilder<>(this, this::command);
    return mapBuilder;
  }
}
