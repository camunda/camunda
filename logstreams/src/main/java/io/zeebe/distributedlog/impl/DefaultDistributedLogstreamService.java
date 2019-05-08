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
package io.zeebe.distributedlog.impl;

import io.atomix.primitive.service.AbstractPrimitiveService;
import io.atomix.primitive.service.BackupInput;
import io.atomix.primitive.service.BackupOutput;
import io.atomix.primitive.service.ServiceExecutor;
import io.atomix.primitive.service.impl.DefaultServiceExecutor;
import io.atomix.protocols.raft.impl.RaftContext;
import io.atomix.protocols.raft.service.RaftServiceContext;
import io.zeebe.distributedlog.DistributedLogstreamClient;
import io.zeebe.distributedlog.DistributedLogstreamService;
import io.zeebe.distributedlog.DistributedLogstreamType;
import io.zeebe.distributedlog.StorageConfiguration;
import io.zeebe.distributedlog.restore.RestoreClient;
import io.zeebe.distributedlog.restore.log.LogReplicationAppender;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.servicecontainer.ServiceContainer;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultDistributedLogstreamService
    extends AbstractPrimitiveService<DistributedLogstreamClient>
    implements DistributedLogstreamService, LogReplicationAppender {

  private static final Logger LOG =
      LoggerFactory.getLogger(DefaultDistributedLogstreamService.class);

  private LogStream logStream;
  private LogStorage logStorage;
  private long lastPosition;
  private String currentLeader;
  private long currentLeaderTerm = -1;

  private String logName;

  private ServiceContainer serviceContainer;
  private RestoreClient restoreClient;
  private ServiceExecutor serviceExecutor;

  public DefaultDistributedLogstreamService(DistributedLogstreamServiceConfig config) {
    super(DistributedLogstreamType.instance(), DistributedLogstreamClient.class);
    lastPosition = -1;
  }

  @Override
  protected void configure(ServiceExecutor executor) {
    super.configure(executor);

    serviceExecutor = executor;
    try {
      logName = getRaftPartitionName(executor);
      LOG.info(
          "Configuring {} on node {} with logName {}",
          getServiceName(),
          getLocalMemberId().id(),
          logName);

      createLogStream(logName);
    } catch (Exception e) {
      LOG.error(
          "Failed to configure {} on node {} with logName {}",
          getServiceName(),
          getLocalMemberId().id(),
          logName,
          e);
      throw e;
    }
  }

  private String getRaftPartitionName(ServiceExecutor executor) {
    String name = getServiceName();
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
    } catch (NoSuchFieldException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    return name;
  }

  private void createLogStream(String logServiceName) {
    // A hack to get partitionId from the name
    final String[] splitted = logServiceName.split("-");
    final int partitionId = Integer.parseInt(splitted[splitted.length - 1]);

    final String localMemberId = getLocalMemberId().id();
    serviceContainer = LogstreamConfig.getServiceContainer(localMemberId);
    restoreClient = LogstreamConfig.getRestoreClient(localMemberId, partitionId);

    if (serviceContainer.hasService(LogStreamServiceNames.logStreamServiceName(logServiceName))) {
      logStream = LogstreamConfig.getLogStream(localMemberId, partitionId);
    } else {

      final StorageConfiguration config =
          LogstreamConfig.getConfig(localMemberId, partitionId).join();

      final File logDirectory = config.getLogDirectory();
      final File snapshotDirectory = config.getSnapshotsDirectory();
      final File blockIndexDirectory = config.getBlockIndexDirectory();

      final StateStorage stateStorage = new StateStorage(blockIndexDirectory, snapshotDirectory);

      logStream =
          LogStreams.createFsLogStream(partitionId)
              .logDirectory(logDirectory.getAbsolutePath())
              .logSegmentSize((int) config.getLogSegmentSize())
              .indexBlockSize((int) config.getIndexBlockSize())
              .logName(logServiceName)
              .serviceContainer(serviceContainer)
              .indexStateStorage(stateStorage)
              .build()
              .join();
    }
    this.logStorage = this.logStream.getLogStorage();

    LogstreamConfig.putLogStream(localMemberId, partitionId, logStream);

    final BufferedLogStreamReader reader = new BufferedLogStreamReader(logStream);
    reader.seekToLastEvent();
    lastPosition = reader.getPosition(); // position of last event which is committed

    LOG.info("Logstreams created. last appended event at position {}", lastPosition);
  }

  @Override
  public long append(String nodeId, long commitPosition, byte[] blockBuffer) {
    // Assumption: first append is always called after claim leadership. So currentLeader is not
    // null. Assumption is also valid during restart.
    if (!currentLeader.equals(nodeId)) {
      LOG.warn(
          "Append request from follower node {}. Current leader is {}.", nodeId, currentLeader);
      return 0; // Don't return a error, so that the appender never retries. TODO: Return a proper
      // error code;
    }

    return append(commitPosition, blockBuffer);
  }

  @Override
  public long append(long commitPosition, byte[] blockBuffer) {
    if (commitPosition <= lastPosition) {
      // This case can happen due to raft-replay or when appender retries due to timeout or other
      // exceptions.
      LOG.trace("Rejecting append request at position {}", commitPosition);
      return 1; // Assume the append was successful because event was previously appended.
    }

    final ByteBuffer buffer = ByteBuffer.wrap(blockBuffer);
    final long appendResult = logStorage.append(buffer);
    if (appendResult > 0) {
      // Following is required to trigger the commit listeners.
      logStream.setCommitPosition(commitPosition);
      lastPosition = commitPosition;
    }
    // the return result is valid only for the leader. If the followers failed to append, they don't
    // retry
    return appendResult;
  }

  @Override
  public boolean claimLeaderShip(String nodeId, long term) {
    LOG.debug(
        "Node {} claiming leadership for logstream partition {} at term {}.",
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
  public void backup(BackupOutput backupOutput) {
    // This doesn't back up the events in the logStream. All the log entries occurred before the
    // backup snapshot, but not appended to logStorage may be lost. So, if this node is away for a
    // while and tries to recover with backup received from other nodes, there will be missing
    // entries in the logStorage.

    LOG.info("Backup log {}", logName);
    // Backup in-memory states
    backupOutput.writeLong(lastPosition);
    backupOutput.writeString(currentLeader);
    backupOutput.writeLong(currentLeaderTerm);
  }

  @Override
  public void restore(BackupInput backupInput) {
    // restore in-memory states
    final long backupPosition = backupInput.readLong();
    LOG.info("Restoring log");
    if (lastPosition < backupPosition) {
      LOG.error(
          "There are missing events in the logstream. last event in logstream is {}. backup position is {}.",
          lastPosition,
          backupPosition);
    }
    currentLeader = backupInput.readString();
    currentLeaderTerm = backupInput.readLong();
  }

  @Override
  public void close() {
    super.close();
    LOG.info("Closing {}", getServiceName());
  }
}
