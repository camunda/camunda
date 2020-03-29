/*
 * Copyright 2017-present Open Networking Foundation
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
package io.atomix.primitive.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import io.atomix.primitive.AsyncPrimitive;
import io.atomix.primitive.PrimitiveState;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.protocol.PrimitiveProtocol;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Base class for primitive delegates. */
public abstract class DelegatingAsyncPrimitive<T extends AsyncPrimitive> implements AsyncPrimitive {
  private final T primitive;

  public DelegatingAsyncPrimitive(final T primitive) {
    this.primitive = checkNotNull(primitive);
  }

  /**
   * Returns the delegate primitive.
   *
   * @return the underlying primitive
   */
  protected T delegate() {
    return primitive;
  }

  @Override
  public String name() {
    return primitive.name();
  }

  @Override
  public PrimitiveType type() {
    return primitive.type();
  }

  @Override
  public PrimitiveProtocol protocol() {
    return primitive.protocol();
  }

  @Override
  public void addStateChangeListener(final Consumer<PrimitiveState> listener) {
    primitive.addStateChangeListener(listener);
  }

  @Override
  public void removeStateChangeListener(final Consumer<PrimitiveState> listener) {
    primitive.removeStateChangeListener(listener);
  }

  @Override
  public CompletableFuture<Void> close() {
    return primitive.close();
  }

  @Override
  public CompletableFuture<Void> delete() {
    return primitive.delete();
  }

  @Override
  public int hashCode() {
    return Objects.hash(primitive);
  }

  @Override
  public boolean equals(final Object other) {
    return other instanceof DelegatingAsyncPrimitive
        && primitive.equals(((DelegatingAsyncPrimitive) other).primitive);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(getClass()).add("delegate", primitive).toString();
  }
}
