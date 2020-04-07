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
package io.atomix.utils.concurrent;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.atomix.utils.concurrent.Threads.namedThreads;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;

/** Single thread context factory. */
public class BlockingAwareSingleThreadContextFactory implements ThreadContextFactory {
  private final ThreadFactory threadFactory;
  private final Executor threadPoolExecutor;

  public BlockingAwareSingleThreadContextFactory(
      final String nameFormat, final int threadPoolSize, final Logger logger) {
    this(threadPoolSize, namedThreads(nameFormat, logger));
  }

  public BlockingAwareSingleThreadContextFactory(
      final int threadPoolSize, final ThreadFactory threadFactory) {
    this(threadFactory, Executors.newScheduledThreadPool(threadPoolSize, threadFactory));
  }

  public BlockingAwareSingleThreadContextFactory(
      final ThreadFactory threadFactory, final Executor threadPoolExecutor) {
    this.threadFactory = checkNotNull(threadFactory);
    this.threadPoolExecutor = checkNotNull(threadPoolExecutor);
  }

  @Override
  public ThreadContext createContext() {
    return new BlockingAwareSingleThreadContext(threadFactory, threadPoolExecutor);
  }
}
