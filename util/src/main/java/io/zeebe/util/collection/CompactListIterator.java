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
package io.zeebe.util.collection;

import java.util.Iterator;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class CompactListIterator implements Iterator<MutableDirectBuffer> {
  protected final CompactList values;
  protected final UnsafeBuffer current;
  protected int position;

  public CompactListIterator(final CompactList values) {
    this.values = values;
    this.current = new UnsafeBuffer(new byte[values.maxElementDataLength()]);

    reset();
  }

  /** Reset the current position of iterator. */
  public void reset() {
    position = -1;
  }

  /**
   * Return the current position of the iterator.
   *
   * @return
   */
  public int position() {
    return position;
  }

  @Override
  public boolean hasNext() {
    return position + 1 < values.size();
  }

  /**
   * Attach a view of the next element to a {@link MutableDirectBuffer} for providing direct access.
   * Always returns the same object, i.e. objects returned by previous {@link #next()} invocations
   * become invalid.
   *
   * @see CompactList#wrap(int, MutableDirectBuffer)
   * @see Iterator#next()
   */
  public MutableDirectBuffer next() {
    if (position + 1 >= values.size()) {
      throw new java.util.NoSuchElementException();
    }

    position++;
    values.wrap(position, current);

    return current;
  }
}
