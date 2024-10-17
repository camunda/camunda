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

import java.util.concurrent.ThreadFactory;
import org.slf4j.MDC;

/**
 * Named thread factory.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class AtomixThreadFactory implements ThreadFactory {

  final String actorSchedulerName;

  public AtomixThreadFactory() {
    actorSchedulerName = "";
  }

  public AtomixThreadFactory(final String actorSchedulerName) {
    this.actorSchedulerName = actorSchedulerName;
  }

  @Override
  public Thread newThread(final Runnable r) {
    return new AtomixThread(
        () -> {
          if (actorSchedulerName != null && !actorSchedulerName.isEmpty()) {
            MDC.put("actor-scheduler", actorSchedulerName);
          }
          r.run();
        });
  }
}
