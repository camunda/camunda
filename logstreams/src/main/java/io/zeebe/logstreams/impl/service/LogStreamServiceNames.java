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

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.logstreams.impl.LogBlockIndexWriter;
import io.zeebe.logstreams.impl.LogStorageAppender;
import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.servicecontainer.ServiceName;

public class LogStreamServiceNames {
  public static final ServiceName<Void> logStreamRootServiceName(String logName) {
    return ServiceName.newServiceName(String.format("logstream.%s", logName), Void.class);
  }

  public static final ServiceName<LogStream> logStreamServiceName(String logName) {
    return ServiceName.newServiceName(
        String.format("logstream.%s.service", logName), LogStream.class);
  }

  public static final ServiceName<LogStorage> logStorageServiceName(String logName) {
    return ServiceName.newServiceName(
        String.format("logstream.%s.storage", logName), LogStorage.class);
  }

  public static final ServiceName<LogStorageAppender> logStorageAppenderServiceName(
      String logName) {
    return ServiceName.newServiceName(
        String.format("logstream.%s.storage.appender", logName), LogStorageAppender.class);
  }

  public static final ServiceName<DistributedLogstreamPartition> distributedLogPartitionServiceName(
      String logName) {
    return ServiceName.newServiceName(
        String.format("logstream.%s.distributed.log", logName),
        DistributedLogstreamPartition.class);
  }

  public static final ServiceName<LogBlockIndex> logBlockIndexServiceName(String logName) {
    return ServiceName.newServiceName(
        String.format("logstream.%s.blockIdx", logName), LogBlockIndex.class);
  }

  public static final ServiceName<LogBlockIndexWriter> logBlockIndexWriterService(String logName) {
    return ServiceName.newServiceName(
        String.format("logstream.%s.blockIdx.writer", logName), LogBlockIndexWriter.class);
  }

  public static final ServiceName<Dispatcher> logWriteBufferServiceName(String logName) {
    return ServiceName.newServiceName(
        String.format("logstream.%s.writeBuffer", logName), Dispatcher.class);
  }

  public static final ServiceName<Subscription> logWriteBufferSubscriptionServiceName(
      String logName, String subscriptionName) {
    return ServiceName.newServiceName(
        String.format("logstream.%s.writeBuffer.subscription.%s", logName, subscriptionName),
        Subscription.class);
  }

  public static final ServiceName<Void> logStorageAppenderRootService(String logName) {
    return ServiceName.newServiceName(
        String.format("logstream.%s.storage.appender-root", logName), Void.class);
  }
}
