/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.primitive.proxy.impl;

import io.atomix.primitive.operation.OperationId;
import io.atomix.primitive.session.SessionId;

/** Container for a distributed log based state machine operation. */
public class LogOperation {
  private final SessionId sessionId;
  private final String primitive;
  private final long operationIndex;
  private final OperationId operationId;
  private final byte[] operation;

  public LogOperation(
      final SessionId sessionId,
      final String primitive,
      final long operationIndex,
      final OperationId operationId,
      final byte[] operation) {
    this.sessionId = sessionId;
    this.primitive = primitive;
    this.operationIndex = operationIndex;
    this.operationId = operationId;
    this.operation = operation;
  }

  /**
   * Returns the primitive session ID.
   *
   * @return the primitive session ID
   */
  public SessionId sessionId() {
    return sessionId;
  }

  /**
   * Returns the primitive name.
   *
   * @return the primitive name
   */
  public String primitive() {
    return primitive;
  }

  /**
   * Returns the write index used to maintain read-after-write consistency.
   *
   * @return the write index
   */
  public long operationIndex() {
    return operationIndex;
  }

  /**
   * Returns the primitive operation ID.
   *
   * @return the primitive operation ID
   */
  public OperationId operationId() {
    return operationId;
  }

  /**
   * Returns the serialized primitive operation.
   *
   * @return the serialized primitive operation
   */
  public byte[] operation() {
    return operation;
  }
}
