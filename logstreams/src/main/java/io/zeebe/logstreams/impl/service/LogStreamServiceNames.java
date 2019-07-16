/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.service;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.logstreams.impl.LogStorageAppender;
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
