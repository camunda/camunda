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
package io.zeebe.logstreams.impl;

import io.zeebe.distributedlog.CommitLogEvent;
import io.zeebe.distributedlog.LogEventListener;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.channel.ActorConditions;
import io.zeebe.util.sched.future.ActorFuture;
import java.nio.ByteBuffer;
import org.slf4j.Logger;

/**
 * Listen to the committed events in DistributedLogstream and appends the bytes to the logstorage.
 * This should run in all replicas including leader and followers.
 */
public class LogStorageCommitListener extends Actor implements LogEventListener {

  public static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;
  private final LogStorage logStorage;
  private final LogStream logStream;
  private final DistributedLogstreamPartition distributedLog;
  private final ActorConditions onLogStorageAppendedConditions;

  private long lastCommittedPosition;

  public LogStorageCommitListener(
      LogStorage logStorage,
      LogStream logStream,
      DistributedLogstreamPartition distributedLog,
      ActorConditions onLogStorageAppendedConditions) {
    this.logStorage = logStorage;
    this.logStream = logStream;
    this.distributedLog = distributedLog;
    this.onLogStorageAppendedConditions = onLogStorageAppendedConditions;
  }

  @Override
  protected void onActorStarting() {
    distributedLog.addListener(this);
  }

  @Override
  public void onCommit(CommitLogEvent event) {
    final byte[] committedBytes = event.getCommittedBytes();
    final long commitPosition = event.getCommitPosition();
    actor.call(() -> append(commitPosition, committedBytes));
  }

  private void append(long commitPosition, byte[] committedBytes) {
    final ByteBuffer buffer = ByteBuffer.wrap(committedBytes);
    logStorage.append(buffer);
    lastCommittedPosition = commitPosition;
    // TODO: (https://github.com/zeebe-io/zeebe/issues/2058)
    onLogStorageAppendedConditions.signalConsumers();
    // Commit position may be not required anymore. https://github.com/zeebe-io/zeebe/issues/2058.
    // Following is required to trigger the commit listeners.
    logStream.setCommitPosition(commitPosition);
  }

  public ActorFuture<?> close() {
    distributedLog.removeListener(this);
    return actor.close();
  }
}
