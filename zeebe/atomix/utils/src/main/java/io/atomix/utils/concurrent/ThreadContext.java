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
package io.atomix.utils.concurrent;

import static com.google.common.base.Preconditions.checkState;

import io.camunda.zeebe.util.CloseableSilently;
import java.util.concurrent.Executor;

/**
 * Thread context.
 *
 * <p>The thread context is used by Atomix to determine the correct thread on which to execute
 * asynchronous callbacks. All threads created within Catalyst must be instances of {@link
 * AtomixThread}. Once a thread has been created, the context is stored in the thread object via
 * {@link AtomixThread#setContext(ThreadContext)}. This means there is a one-to-one relationship
 * between a context and a thread. That is, a context is representative of a thread and provides an
 * interface for firing events on that thread.
 *
 * <p>Components of the framework that provide custom threads should use {@link AtomixThreadFactory}
 * to allocate new threads and provide a custom {@link ThreadContext} implementation.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public interface ThreadContext extends CloseableSilently, Executor, Scheduler {

  /**
   * Returns the current thread context.
   *
   * @return The current thread context or {@code null} if no context exists.
   */
  static ThreadContext currentContext() {
    final Thread thread = Thread.currentThread();
    return thread instanceof AtomixThread atomixThread ? atomixThread.getContext() : null;
  }

  /** Checks that the current thread is the correct context thread. */
  default void checkThread() {
    checkState(currentContext() == this, "not on the expected thread");
  }

  /** By default there are no resources to close. */
  @Override
  default void close() {}
}
