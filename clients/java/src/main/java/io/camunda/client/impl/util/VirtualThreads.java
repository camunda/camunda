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
package io.camunda.client.impl.util;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Creates virtual-thread executors on JVMs that support them (JDK 21+), while the client itself
 * stays compiled for Java 8. The JDK API is looked up reflectively, so on older JVMs, or on JDK
 * 19/20 without preview features enabled, no executor is returned and callers fall back to platform
 * threads.
 */
public final class VirtualThreads {

  /** Thread name prefix of the virtual threads that run job handlers. */
  public static final String JOB_WORKER_THREAD_NAME_PREFIX = "job-worker-virtual-";

  private static final Method OF_VIRTUAL;
  private static final Method NAME;
  private static final Method FACTORY;
  private static final Method NEW_THREAD_PER_TASK_EXECUTOR;

  static {
    Method ofVirtual = null;
    Method name = null;
    Method factory = null;
    Method newThreadPerTaskExecutor = null;
    try {
      final Class<?> threadBuilder = Class.forName("java.lang.Thread$Builder");
      ofVirtual = Thread.class.getMethod("ofVirtual");
      name = threadBuilder.getMethod("name", String.class, long.class);
      factory = threadBuilder.getMethod("factory");
      newThreadPerTaskExecutor =
          Executors.class.getMethod("newThreadPerTaskExecutor", ThreadFactory.class);
    } catch (final ReflectiveOperationException | LinkageError e) {
      ofVirtual = null;
    }
    OF_VIRTUAL = ofVirtual;
    NAME = name;
    FACTORY = factory;
    NEW_THREAD_PER_TASK_EXECUTOR = newThreadPerTaskExecutor;
  }

  private VirtualThreads() {}

  /**
   * @param threadNamePrefix prefix of the created threads' names, followed by a counter
   * @return an executor that starts a new virtual thread for each task, or empty if this JVM does
   *     not support virtual threads
   */
  public static Optional<ExecutorService> newThreadPerTaskExecutor(final String threadNamePrefix) {
    if (OF_VIRTUAL == null) {
      return Optional.empty();
    }
    try {
      final Object builder = NAME.invoke(OF_VIRTUAL.invoke(null), threadNamePrefix, 0L);
      final ThreadFactory threadFactory = (ThreadFactory) FACTORY.invoke(builder);
      return Optional.of(
          (ExecutorService) NEW_THREAD_PER_TASK_EXECUTOR.invoke(null, threadFactory));
    } catch (final ReflectiveOperationException | RuntimeException e) {
      return Optional.empty();
    }
  }
}
