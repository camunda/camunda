/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.impl;

import io.atomix.primitive.service.AbstractPrimitiveService;
import io.atomix.primitive.service.BackupInput;
import io.atomix.primitive.service.BackupOutput;
import io.atomix.primitive.service.ServiceExecutor;
import io.atomix.primitive.service.impl.DefaultServiceExecutor;
import io.atomix.protocols.raft.impl.RaftContext;
import io.atomix.protocols.raft.service.RaftServiceContext;
import io.atomix.utils.concurrent.SingleThreadContext;
import io.atomix.utils.concurrent.ThreadContext;
import io.zeebe.distributedlog.DistributedLogstreamClient;
import io.zeebe.distributedlog.DistributedLogstreamService;
import io.zeebe.distributedlog.DistributedLogstreamType;
import io.zeebe.distributedlog.StorageConfiguration;
import io.zeebe.distributedlog.restore.RestoreClient;
import io.zeebe.distributedlog.restore.RestoreFactory;
import io.zeebe.distributedlog.restore.RestoreNodeProvider;
import io.zeebe.distributedlog.restore.impl.RestoreController;
import io.zeebe.distributedlog.restore.log.LogReplicationAppender;
import io.zeebe.distributedlog.restore.log.LogReplicator;
import io.zeebe.distributedlog.restore.snapshot.RestoreSnapshotReplicator;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreContext;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.state.FileSnapshotConsumer;
import io.zeebe.logstreams.state.SnapshotConsumer;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.util.ZbLogger;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultDistributedLogstreamService
    extends AbstractPrimitiveService<DistributedLogstreamClient>
    implements DistributedLogstreamService, LogReplicationAppender {

  private static final Logger LOG =
      LoggerFactory.getLogger(DefaultDistributedLogstreamService.class);

  private LogStream logStream;
  private LogStorage logStorage;
  private String logName;
  private int partitionId;
  private String currentLeader;
  private long currentLeaderTerm = -1;
  private long lastPosition;
  private ServiceContainer serviceContainer;
  private String localMemberId;
  private Logger logger;

  public DefaultDistributedLogstreamService() {
    super(DistributedLogstreamType.instance(), DistributedLogstreamClient.class);
    lastPosition = -1;
  }

  @Override
  protected void configure(ServiceExecutor executor) {
    super.configure(executor);
    localMemberId = getLocalMemberId().id();

    try {
      logName = getRaftPartitionName(executor);
      configureFromLogName(logName);

      logger.debug(
          "Configuring {} on node {} with logName {}",
          getServiceName(),
          getLocalMemberId().id(),
          logName);
      logStream = getOrCreateLogStream(logName);
      logStorage = logStream.getLogStorage();
      initLastPosition();
      logger.debug(
          "Configured with LogStream {} and last appended event at position {}",
          logName,
          lastPosition);
    } catch (Exception e) {
      // use static logger since we may have failed before we got the log name and so have no
      // configured logger yet
      LOG.error(
          "Failed to configure {} on node {} with logName {}",
          getServiceName(),
          getLocalMemberId().id(),
          logName,
          e);
      throw e;
    }
  }

  private void configureFromLogName(String logName) {
    partitionId = getPartitionIdFromLogName(logName);
    logger = new ZbLogger(String.format("%s-%d", this.getClass().getName(), partitionId));
  }

  private int getPartitionIdFromLogName(String logName) {
    final String[] parts = logName.split("-");
    return Integer.valueOf(parts[parts.length - 1]);
  }

  private String getRaftPartitionName(ServiceExecutor executor) {
    final String name;

    try {
      final Field context = DefaultServiceExecutor.class.getDeclaredField("context");
      context.setAccessible(true);
      final RaftServiceContext raftServiceContext = (RaftServiceContext) context.get(executor);
      final Field raft = RaftServiceContext.class.getDeclaredField("raft");
      raft.setAccessible(true);
      final RaftContext raftContext = (RaftContext) raft.get(raftServiceContext);
      name = raftContext.getName();
      raft.setAccessible(false);
      context.setAccessible(false);
    } catch (IllegalAccessException | NoSuchFieldException e) {
      throw new RuntimeException(e);
    }

    return name;
  }

  private LogStream getOrCreateLogStream(String logServiceName) {
    final LogStream logStream;
    serviceContainer = LogstreamConfig.getServiceContainer(localMemberId);

    if (serviceContainer
        .hasService(LogStreamServiceNames.logStreamServiceName(logServiceName))
        .join()) {
      logStream = LogstreamConfig.getLogStream(localMemberId, partitionId);
    } else {
      logStream = createLogStream(logServiceName);
    }

    LogstreamConfig.putLogStream(localMemberId, partitionId, logStream);
    return logStream;
  }

  private LogStream createLogStream(String logServiceName) {
    final StorageConfiguration config =
        LogstreamConfig.getConfig(localMemberId, partitionId).join();

    final File logDirectory = config.getLogDirectory();

    return LogStreams.createFsLogStream(partitionId)
        .logDirectory(logDirectory.getAbsolutePath())
        .logSegmentSize((int) config.getLogSegmentSize())
        .logName(logServiceName)
        .serviceContainer(serviceContainer)
        .build()
        .join();
  }

  private void initLastPosition() {
    final BufferedLogStreamReader reader = new BufferedLogStreamReader(logStream);
    reader.seekToLastEvent();
    lastPosition = reader.getPosition();
    if (lastPosition > 0) {
      logStream.setCommitPosition(lastPosition);
    }
    reader.close();
  }

  @Override
  public long append(String nodeId, long commitPosition, byte[] blockBuffer) {
    // Assumption: first append is always called after claim leadership. So currentLeader is not
    // null. Assumption is also valid during restart.
    if (!currentLeader.equals(nodeId)) {
      logger.warn(
          "Append request from follower node {}. Current leader is {}.", nodeId, currentLeader);
      return 0; // Don't return a error, so that the appender never retries. TODO: Return a proper
      // error code;
    }

    return append(commitPosition, blockBuffer);
  }

  @Override
  public boolean claimLeaderShip(String nodeId, long term) {
    logger.debug(
        "Node {} claiming leadership for LogStream partition {} at term {}.",
        nodeId,
        logStream.getPartitionId(),
        term);

    if (currentLeaderTerm < term) {
      this.currentLeader = nodeId;
      this.currentLeaderTerm = term;
      return true;
    }
    return false;
  }

  @Override
  public long append(long commitPosition, byte[] blockBuffer) {
    if (commitPosition <= lastPosition) {
      // This case can happen due to raft-replay or when appender retries due to timeout or other
      // exceptions.
      logger.trace("Rejecting append request at position {}", commitPosition);
      return 1; // Assume the append was successful because event was previously appended.
    }

    final ByteBuffer buffer = ByteBuffer.wrap(blockBuffer);
    final long appendResult = appendBlock(buffer);
    updateCommitPosition(commitPosition);
    return appendResult;
  }

  private long appendBlock(ByteBuffer buffer) {
    long appendResult = -1;
    boolean notAppended = true;
    try {

      do {
        try {
          appendResult = logStorage.append(buffer);
          notAppended = false;
        } catch (IOException ioe) {
          // we want to retry the append
          // we avoid recursion, otherwise we can get stack overflow exceptions
          LOG.error(
              "Expected to append new buffer, but caught IOException. Will retry this operation.",
              ioe);
        }
      } while (notAppended);
    } catch (Exception e) {
      // block the primitive
      LOG.error(
          "Expected to append new buffer, but caught an non recoverable exception. Will block the primitive.",
          e);
      while (true) {
        try {
          new CountDownLatch(1).await();
        } catch (InterruptedException ex) {
          LOG.error("Blocking the primitive was interrupted.", ex);
        }
      }
    }

    return appendResult;
  }

  @Override
  public void backup(BackupOutput backupOutput) {
    // This doesn't back up the events in the logStream. All the log entries occurred before the
    // backup snapshot, but not appended to logStorage may be lost. So, if this node is away for a
    // while and tries to recover with backup received from other nodes, there will be missing
    // entries in the logStorage.
    logger.info("Backup log {} at position {}", logName, lastPosition);

    // Backup in-memory states
    backupOutput.writeLong(lastPosition);
    backupOutput.writeString(currentLeader);
    backupOutput.writeLong(currentLeaderTerm);
  }

  @Override
  public void restore(BackupInput backupInput) {
    final long backupPosition = backupInput.readLong();

    if (lastPosition < backupPosition) {
      LogstreamConfig.startRestore(localMemberId, partitionId);

      final ThreadContext restoreThreadContext =
          new SingleThreadContext(String.format("log-restore-%d", partitionId));
      final RestoreController restoreController = createRestoreController(restoreThreadContext);

      while (lastPosition < backupPosition) {
        final long latestLocalPosition = lastPosition;
        logger.debug(
            "Restoring local log from position {} to {}", latestLocalPosition, backupPosition);
        try {
          lastPosition = restoreController.restore(lastPosition, backupPosition);
          logger.debug(
              "Restored local log from position {} to {}", latestLocalPosition, lastPosition);
        } catch (RuntimeException e) {
          lastPosition = logStream.getCommitPosition();
          logger.debug("Restoring local log failed at position {}, retrying.", lastPosition, e);
        }
      }

      restoreThreadContext.close();
      LogstreamConfig.completeRestore(localMemberId, partitionId);
    }

    logger.debug("Restored local log to position {}", lastPosition);
    currentLeader = backupInput.readString();
    currentLeaderTerm = backupInput.readLong();
  }

  @Override
  public void close() {
    super.close();
    logger.info("Closing {}", getServiceName());
  }

  private RestoreController createRestoreController(ThreadContext restoreThreadContext) {
    final RestoreFactory restoreFactory = LogstreamConfig.getRestoreFactory(localMemberId);
    final RestoreClient restoreClient = restoreFactory.createClient(partitionId);
    final RestoreNodeProvider nodeProvider = restoreFactory.createNodeProvider(partitionId);
    final LogReplicator logReplicator =
        new LogReplicator(this, restoreClient, restoreThreadContext);

    final SnapshotRestoreContext snapshotRestoreContext =
        restoreFactory.createSnapshotRestoreContext(partitionId, logger);
    final StateStorage storage = snapshotRestoreContext.getStateStorage();
    final SnapshotConsumer snapshotConsumer = new FileSnapshotConsumer(storage, LOG);

    final RestoreSnapshotReplicator snapshotReplicator =
        new RestoreSnapshotReplicator(
            restoreClient, snapshotRestoreContext, snapshotConsumer, restoreThreadContext, logger);
    return new RestoreController(
        restoreClient,
        nodeProvider,
        logReplicator,
        snapshotReplicator,
        restoreThreadContext,
        logger);
  }

  private void updateCommitPosition(long commitPosition) {
    logStream.setCommitPosition(commitPosition);
    lastPosition = commitPosition;
  }
}
