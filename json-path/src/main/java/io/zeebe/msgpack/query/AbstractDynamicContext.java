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
package io.zeebe.msgpack.query;

import io.zeebe.util.allocation.HeapBufferAllocator;
import io.zeebe.util.collection.CompactList;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public abstract class AbstractDynamicContext {

  protected CompactList context;
  protected UnsafeBuffer cursorView = new UnsafeBuffer(0, 0);
  protected UnsafeBuffer dynamicContextView = new UnsafeBuffer(0, 0);

  protected int dynamicContextSize;
  protected int elementSize;
  protected int staticElementSize;

  protected DirectBuffer emptyElement;

  public AbstractDynamicContext(int capacity, int staticElementSize, int dynamicContextSize) {
    this.staticElementSize = staticElementSize;
    this.dynamicContextSize = dynamicContextSize;
    this.elementSize = staticElementSize + dynamicContextSize;
    context = new CompactList(elementSize, capacity, new HeapBufferAllocator());
    emptyElement = new UnsafeBuffer(new byte[elementSize]);
  }

  public boolean hasElements() {
    return context.size() > 0;
  }

  public int size() {
    return context.size();
  }

  // cursor operations

  public void moveTo(int element) {
    context.wrap(element, cursorView);
  }

  public void moveToLastElement() {
    context.wrap(context.size() - 1, cursorView);
  }

  public void appendElement() {
    context.add(emptyElement, 0, elementSize);
    moveToLastElement();
  }

  public void removeLastElement() {
    context.remove(context.size() - 1);

    if (size() > 0) {
      moveToLastElement();
    }
  }

  public MutableDirectBuffer dynamicContext() {
    if (dynamicContextSize > 0) {
      dynamicContextView.wrap(cursorView, staticElementSize, dynamicContextSize);
    } else {
      dynamicContextView.wrap(0, 0);
    }
    return dynamicContextView;
  }

  public void clear() {
    context.clear();
  }
}
