/*
 * Copyright 2018-present Open Networking Foundation
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

import static io.atomix.utils.concurrent.Threads.namedThreads;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/** Blocking aware single thread context. */
public class BlockingAwareSingleThreadContext extends SingleThreadContext {
  private final Executor threadPoolExecutor;

  public BlockingAwareSingleThreadContext(
      final String nameFormat, final Executor threadPoolExecutor) {
    this(namedThreads(nameFormat, LOGGER), threadPoolExecutor);
  }

  public BlockingAwareSingleThreadContext(
      final ThreadFactory factory, final Executor threadPoolExecutor) {
    super(factory);
    this.threadPoolExecutor = threadPoolExecutor;
  }

  @Override
  public void execute(final Runnable command) {
    if (isBlocked()) {
      threadPoolExecutor.execute(command);
    } else {
      super.execute(command);
    }
  }
}
