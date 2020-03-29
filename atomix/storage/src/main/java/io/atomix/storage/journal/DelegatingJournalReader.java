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
package io.atomix.storage.journal;

import static com.google.common.base.MoreObjects.toStringHelper;

/** Journal reader delegate. */
public class DelegatingJournalReader<E> implements JournalReader<E> {
  private final JournalReader<E> delegate;

  public DelegatingJournalReader(final JournalReader<E> delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public long getFirstIndex() {
    return delegate.getFirstIndex();
  }

  @Override
  public long getLastIndex() {
    return delegate.getLastIndex();
  }

  @Override
  public long getCurrentIndex() {
    return delegate.getCurrentIndex();
  }

  @Override
  public Indexed<E> getCurrentEntry() {
    return delegate.getCurrentEntry();
  }

  @Override
  public long getNextIndex() {
    return delegate.getNextIndex();
  }

  @Override
  public boolean hasNext() {
    return delegate.hasNext();
  }

  @Override
  public Indexed<E> next() {
    return delegate.next();
  }

  @Override
  public void reset() {
    delegate.reset();
  }

  @Override
  public void reset(final long index) {
    delegate.reset(index);
  }

  @Override
  public void close() {
    delegate.close();
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("delegate", delegate).toString();
  }
}
