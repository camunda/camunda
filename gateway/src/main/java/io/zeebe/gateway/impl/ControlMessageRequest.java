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
package io.zeebe.gateway.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.gateway.impl.RequestManager.ResponseFuture;
import io.zeebe.protocol.clientapi.ControlMessageType;

public abstract class ControlMessageRequest<R> {

  protected final ControlMessageType type;
  protected int targetPartition;
  protected final Class<? extends R> responseClass;

  protected final RequestManager client;

  /** Constructor for requests addressing any broker */
  public ControlMessageRequest(
      final RequestManager client,
      final ControlMessageType type,
      final Class<? extends R> responseClass) {
    this(client, type, -1, responseClass);
  }

  public ControlMessageRequest(
      final RequestManager client,
      final ControlMessageType type,
      final int targetPartition,
      final Class<? extends R> responseClass) {
    this.client = client;
    this.type = type;
    this.targetPartition = targetPartition;
    this.responseClass = responseClass;
  }

  @JsonIgnore
  public ControlMessageType getType() {
    return type;
  }

  @JsonIgnore
  public int getTargetPartition() {
    return targetPartition;
  }

  public void setTargetPartition(final int targetPartition) {
    this.targetPartition = targetPartition;
  }

  @JsonIgnore
  public Class<? extends R> getResponseClass() {
    return responseClass;
  }

  public void onResponse(final R response) {}

  public abstract Object getRequest();

  public ResponseFuture<R> send() {
    return client.send(this);
  }
}
