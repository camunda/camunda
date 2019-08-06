/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog;

import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.distributedLogPartitionServiceName;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.logStreamServiceName;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.sched.future.ActorFuture;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributedLogPartitionRule {

  public static final Logger LOG = LoggerFactory.getLogger("io.zeebe.distributedlog.test");
  private static long leaderTerm = 0;
  private final ServiceContainer serviceContainer;
  private final int partition;
  private final int nodeId;
  private final LogStreamWriterImpl writer = new LogStreamWriterImpl();

  private final RecordMetadata metadata = new RecordMetadata();
  private final String logName;
  private LogStream logStream;
  private BufferedLogStreamReader reader;

  public DistributedLogPartitionRule(
      ServiceContainer serviceContainer, int nodeId, int partition, Path rootDirectory)
      throws IOException {
    this.serviceContainer = serviceContainer;
    this.nodeId = nodeId;
    this.partition = partition;
    this.logName = String.format("raft-atomix-partition-%d", this.partition);
  }

  public void start() {
    getLogStream();
  }

  public void close() {
    logStream.closeAppender().join(); // If opened
    serviceContainer.removeService(distributedLogPartitionServiceName(logName));
    logStream.close();
  }

  private void getLogStream() {
    final ServiceName<Void> testService =
        ServiceName.newServiceName(String.format("test-%s", logName), Void.class);
    final Injector<LogStream> logStreamInjector = new Injector<>();
    serviceContainer
        .createService(testService, () -> null)
        .dependency(logStreamServiceName(logName), logStreamInjector)
        .install()
        .join();

    logStream = logStreamInjector.getValue();
    assertThat(logStream).isNotNull();
    reader = new BufferedLogStreamReader(logStream);
  }

  private void createDistributedLog() {
    final DistributedLogstreamPartition log =
        new DistributedLogstreamPartition(partition, leaderTerm++);
    final ActorFuture<DistributedLogstreamPartition> installFuture =
        serviceContainer
            .createService(distributedLogPartitionServiceName(logName), log)
            .dependency(DistributedLogRule.ATOMIX_SERVICE_NAME, log.getAtomixInjector())
            .install();
    installFuture.join();
  }

  public void becomeLeader() {
    if (!serviceContainer.hasService(distributedLogPartitionServiceName(logName)).join()) {
      createDistributedLog();
    }

    logStream.openAppender().join();
  }

  public void becomeFollower() {
    logStream.closeAppender().join(); // If opened
    serviceContainer.removeService(distributedLogPartitionServiceName(logName)).join();
  }

  public boolean eventAppended(String message, long writePosition) {
    reader.seek(writePosition);
    if (reader.hasNext()) {
      final LoggedEvent event = reader.next();
      final String messageRead =
          bufferAsString(event.getValueBuffer(), event.getValueOffset(), event.getValueLength());
      final long eventPosition = event.getPosition();
      return (message.equals(messageRead) && eventPosition == writePosition);
    }
    return false;
  }

  public long writeEvent(final String message) {
    writer.wrap(logStream);

    final AtomicLong writePosition = new AtomicLong();
    final DirectBuffer value = wrapString(message);

    TestUtil.doRepeatedly(
            () -> writer.key(-1).metadataWriter(metadata.reset()).value(value).tryWrite())
        .until(
            position -> {
              if (position != null && position >= 0) {
                writePosition.set(position);
                return true;
              } else {
                return false;
              }
            },
            "Failed to write event with message {}",
            message);
    return writePosition.get();
  }

  public int getCommittedEventsCount() {
    int numEvents = 0;
    reader.seekToFirstEvent();
    while (reader.hasNext()) {
      reader.next();
      numEvents++;
    }
    return numEvents;
  }
}
