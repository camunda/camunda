/*
 * Copyright 2016-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.primitive.session.impl;

import io.atomix.primitive.operation.OperationId;
import io.atomix.primitive.operation.OperationType;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;

/**
 * ID generator operations.
 *
 * <p>WARNING: Do not refactor enum values. Only add to them. Changing values risk breaking the
 * ability to backup/restore/upgrade clusters.
 */
public enum SessionIdGeneratorOperations implements OperationId {
  NEXT(OperationType.COMMAND);

  public static final Namespace NAMESPACE =
      Namespace.builder()
          .register(Namespaces.BASIC)
          .nextId(Namespaces.BEGIN_USER_CUSTOM_ID)
          .build(SessionIdGeneratorOperations.class.getSimpleName());
  private final OperationType type;

  SessionIdGeneratorOperations(final OperationType type) {
    this.type = type;
  }

  @Override
  public String id() {
    return name();
  }

  @Override
  public OperationType type() {
    return type;
  }
}
