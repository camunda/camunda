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

import org.slf4j.Logger;

/** Raft thread model. */
public enum ThreadModel {

  /** A thread model that creates a thread pool to be shared by all services. */
  SHARED_THREAD_POOL {
    @Override
    public ThreadContextFactory factory(
        final String nameFormat, final int threadPoolSize, final Logger logger) {
      return new BlockingAwareThreadPoolContextFactory(nameFormat, threadPoolSize, logger);
    }
  },

  /** A thread model that creates a thread for each Raft service. */
  THREAD_PER_SERVICE {
    @Override
    public ThreadContextFactory factory(
        final String nameFormat, final int threadPoolSize, final Logger logger) {
      return new BlockingAwareSingleThreadContextFactory(nameFormat, threadPoolSize, logger);
    }
  };

  /**
   * Returns a thread context factory.
   *
   * @param nameFormat the thread name format
   * @param threadPoolSize the thread pool size
   * @param logger the thread logger
   * @return the thread context factory
   */
  public abstract ThreadContextFactory factory(
      String nameFormat, int threadPoolSize, Logger logger);
}
