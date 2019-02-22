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
package io.zeebe.raft.controller;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.raft.Loggers;
import io.zeebe.raft.Raft;
import io.zeebe.raft.event.RaftEvent;
import io.zeebe.raft.protocol.ConfigurationResponse;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.ScheduledTimer;
import java.time.Duration;
import org.slf4j.Logger;

public class AppendRaftEventController {
  private static final Logger LOG = Loggers.RAFT_LOGGER;
  public static final Duration COMMIT_TIMEOUT = Duration.ofSeconds(15);

  private final Raft raft;
  private final ActorControl actor;

  private final RaftEvent raftEvent = new RaftEvent();
  private final ConfigurationResponse configurationResponse = new ConfigurationResponse();
  private final ActorCondition actorCondition;

  private long position;

  // response state
  private ServerOutput serverOutput;
  private RemoteAddress remoteAddress;
  private long requestId;

  private boolean isHandlingConfigurationChange = false;
  private ScheduledTimer appendRetry;

  public AppendRaftEventController(final Raft raft, ActorControl actorControl) {
    this.raft = raft;
    this.actor = actorControl;

    this.actorCondition = actor.onCondition("raft-event-commited", this::onCommitPositionUpdated);
  }

  public void close() {
    raft.getLogStream().removeOnCommitPositionUpdatedCondition(actorCondition);
    actorCondition.cancel();

    if (appendRetry != null) {
      appendRetry.cancel();
    }
  }

  public void prepare(
      final ServerOutput serverOutput,
      final RemoteAddress remoteAddress,
      final long requestId,
      final RaftIntent intent) {
    this.serverOutput = serverOutput;
    this.remoteAddress = remoteAddress;
    this.requestId = requestId;
    this.isHandlingConfigurationChange = true;
    this.raftEvent.setIntent(intent);
  }

  public void reset() {
    this.raft.getLogStream().removeOnCommitPositionUpdatedCondition(actorCondition);

    this.serverOutput = null;
    this.remoteAddress = null;
    this.requestId = -1;
    this.isHandlingConfigurationChange = false;

    if (appendRetry != null) {
      appendRetry.cancel();
    }
  }

  public void appendEvent() {
    actor.runUntilDone(
        () -> {
          final long position = raftEvent.tryWrite(raft);

          if (position >= 0) {
            actor.done();

            this.position = position;

            final LogStream logStream = this.raft.getLogStream();

            logStream.registerOnCommitPositionUpdatedCondition(actorCondition);

            this.appendRetry =
                actor.runDelayed(
                    COMMIT_TIMEOUT,
                    () -> {
                      logStream.removeOnCommitPositionUpdatedCondition(actorCondition);
                      actor.submit(this::appendEvent);
                    });
          } else {
            actor.yield();
          }
        });
  }

  private void onCommitPositionUpdated() {
    if (isCommitted()) {
      try {
        LOG.debug("Raft event for term {} was committed on position {}", raft.getTerm(), position);

        // send response
        configurationResponse.reset().setSucceeded(true).setRaft(raft);

        raft.sendResponse(serverOutput, remoteAddress, requestId, configurationResponse);
      } finally {
        reset();
      }
    }
  }

  private boolean isCommitted() {
    return position >= 0 && position <= raft.getLogStream().getCommitPosition();
  }

  public long getPosition() {
    return position;
  }

  public boolean isHandlingConfigurationChange() {
    return isHandlingConfigurationChange;
  }
}
