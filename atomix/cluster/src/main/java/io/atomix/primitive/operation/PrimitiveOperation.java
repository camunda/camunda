/*
 * Copyright 2015-present Open Networking Foundation
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

import static com.google.common.base.MoreObjects.toStringHelper;

import io.atomix.utils.misc.ArraySizeHashPrinter;
import java.util.Arrays;
import java.util.Objects;

/** Base type for Raft state operations. */
public class PrimitiveOperation {

  protected final OperationId id;
  protected final byte[] value;

  protected PrimitiveOperation() {
    this.id = null;
    this.value = null;
  }

  public PrimitiveOperation(final OperationId id, final byte[] value) {
    this.id = id;
    this.value = value;
  }

  /**
   * Creates a new primitive operation with a simplified identifier and a null value.
   *
   * @param id the operation identifier
   * @return the primitive operation
   */
  public static PrimitiveOperation operation(final OperationId id) {
    return new PrimitiveOperation(OperationId.simplify(id), null);
  }

  /**
   * Creates a new primitive operation with a simplified identifier.
   *
   * @param id the operation identifier
   * @param value the operation value
   * @return the primitive operation
   */
  public static PrimitiveOperation operation(final OperationId id, final byte[] value) {
    return new PrimitiveOperation(OperationId.simplify(id), value);
  }

  /**
   * Returns the operation identifier.
   *
   * @return the operation identifier
   */
  public OperationId id() {
    return id;
  }

  /**
   * Returns the operation value.
   *
   * @return the operation value
   */
  public byte[] value() {
    return value;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), id, value);
  }

  @Override
  public boolean equals(final Object object) {
    if (object instanceof PrimitiveOperation) {
      final PrimitiveOperation operation = (PrimitiveOperation) object;
      return Objects.equals(operation.id, id) && Arrays.equals(operation.value, value);
    }
    return false;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("id", id)
        .add("value", value != null ? ArraySizeHashPrinter.of(value) : null)
        .toString();
  }
}
