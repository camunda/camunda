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

/** Journal writer delegate. */
public class DelegatingJournalWriter<E> implements JournalWriter<E> {
  private final JournalWriter<E> delegate;

  public DelegatingJournalWriter(final JournalWriter<E> delegate) {
    this.delegate = delegate;
  }

  @Override
  public long getLastIndex() {
    return delegate.getLastIndex();
  }

  @Override
  public Indexed<E> getLastEntry() {
    return delegate.getLastEntry();
  }

  @Override
  public long getNextIndex() {
    return delegate.getNextIndex();
  }

  @Override
  public <T extends E> Indexed<T> append(final T entry) {
    return delegate.append(entry);
  }

  @Override
  public void append(final Indexed<E> entry) {
    delegate.append(entry);
  }

  @Override
  public void commit(final long index) {
    delegate.commit(index);
  }

  @Override
  public void reset(final long index) {
    delegate.reset(index);
  }

  @Override
  public void truncate(final long index) {
    delegate.truncate(index);
  }

  @Override
  public void flush() {
    delegate.flush();
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
