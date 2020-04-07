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

/** Delegating journal. */
public class DelegatingJournal<E> implements Journal<E> {
  private final Journal<E> delegate;

  public DelegatingJournal(final Journal<E> delegate) {
    this.delegate = delegate;
  }

  @Override
  public JournalWriter<E> writer() {
    return delegate.writer();
  }

  @Override
  public JournalReader<E> openReader(final long index) {
    return delegate.openReader(index);
  }

  @Override
  public JournalReader<E> openReader(final long index, final JournalReader.Mode mode) {
    return delegate.openReader(index, mode);
  }

  @Override
  public boolean isOpen() {
    return delegate.isOpen();
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
