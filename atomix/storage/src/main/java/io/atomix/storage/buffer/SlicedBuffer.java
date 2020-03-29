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
package io.atomix.storage.buffer;

/**
 * Sliced buffer.
 *
 * <p>The sliced buffer provides a view of a subset of an underlying buffer. This buffer operates
 * directly on the {@link Bytes} underlying the child {@link Buffer} instance.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class SlicedBuffer extends AbstractBuffer {
  private final Buffer root;

  public SlicedBuffer(
      final Buffer root,
      final Bytes bytes,
      final int offset,
      final int initialCapacity,
      final int maxCapacity) {
    super(bytes, offset, initialCapacity, maxCapacity, null);
    this.root = root;
    root.acquire();
  }

  /**
   * Returns the root buffer.
   *
   * @return The root buffer.
   */
  public Buffer root() {
    return root;
  }

  @Override
  public Buffer duplicate() {
    return new SlicedBuffer(root, bytes, offset(), capacity(), maxCapacity());
  }

  @Override
  public Buffer acquire() {
    root.acquire();
    return this;
  }

  @Override
  public boolean release() {
    return root.release();
  }

  @Override
  public boolean isDirect() {
    return root.isDirect();
  }

  @Override
  public boolean isFile() {
    return root.isFile();
  }

  @Override
  public boolean isReadOnly() {
    return root.isReadOnly();
  }

  @Override
  public Buffer compact() {
    return null;
  }

  @Override
  protected void compact(final int from, final int to, final int length) {
    if (root instanceof AbstractBuffer) {
      ((AbstractBuffer) root).compact(from, to, length);
    }
  }

  @Override
  public void close() {
    root.release();
  }
}
