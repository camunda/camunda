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
package io.zeebe.logstreams.impl.service;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

public class LeaderOpenLogStreamAppenderService implements Service<Void> {
  private LogStream logStream;
  private final Injector<LogStream> logStreamInjector = new Injector<>();

  @Override
  public void start(ServiceStartContext startContext) {
    logStream = logStreamInjector.getValue();
    startContext.async(logStream.openAppender());
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(logStream.closeAppender());
  }

  @Override
  public Void get() {
    return null;
  }

  public Injector<LogStream> getLogStreamInjector() {
    return logStreamInjector;
  }
}
