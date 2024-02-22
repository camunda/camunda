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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;

/** Thread utilities. */
public final class Threads {

  /**
   * Returns a thread factory that produces threads named according to the supplied name pattern.
   *
   * @param pattern name pattern
   * @return thread factory
   */
  public static ThreadFactory namedThreads(final String pattern, final Logger log) {
    return new ThreadFactoryBuilder()
        .setNameFormat(pattern)
        .setThreadFactory(new AtomixThreadFactory())
        .setUncaughtExceptionHandler((t, e) -> log.error("Uncaught exception on " + t.getName(), e))
        .build();
  }
}
