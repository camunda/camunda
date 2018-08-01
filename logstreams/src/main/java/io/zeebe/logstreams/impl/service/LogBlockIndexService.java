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

import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class LogBlockIndexService implements Service<LogBlockIndex> {
  private LogBlockIndex logBlockIndex;

  @Override
  public void start(ServiceStartContext startContext) {
    logBlockIndex = new LogBlockIndex(100000, (c) -> new UnsafeBuffer(ByteBuffer.allocate(c)));
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    logBlockIndex = null;
  }

  @Override
  public LogBlockIndex get() {
    return logBlockIndex;
  }
}
