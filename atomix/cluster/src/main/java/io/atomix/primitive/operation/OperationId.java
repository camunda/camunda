/*
 * Copyright 2017-present Open Networking Foundation
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
package io.atomix.primitive.operation;

import io.atomix.primitive.operation.impl.DefaultOperationId;
import io.atomix.utils.Identifier;

/** Raft operation identifier. */
public interface OperationId extends Identifier<String> {

  /**
   * Returns a new command operation identifier.
   *
   * @param id the command identifier
   * @return the operation identifier
   */
  static OperationId command(final String id) {
    return from(id, OperationType.COMMAND);
  }

  /**
   * Returns a new query operation identifier.
   *
   * @param id the query identifier
   * @return the operation identifier
   */
  static OperationId query(final String id) {
    return from(id, OperationType.QUERY);
  }

  /**
   * Returns a new operation identifier.
   *
   * @param id the operation name
   * @param type the operation type
   * @return the operation identifier
   */
  static OperationId from(final String id, final OperationType type) {
    return new DefaultOperationId(id, type);
  }

  /**
   * Simplifies the given operation identifier.
   *
   * @param operationId the operation identifier to simplify
   * @return the simplified operation identifier
   */
  static OperationId simplify(final OperationId operationId) {
    return new DefaultOperationId(operationId.id(), operationId.type());
  }

  /**
   * Returns the operation type.
   *
   * @return the operation type
   */
  OperationType type();
}
