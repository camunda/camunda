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
package io.zeebe.raft.state;

import io.zeebe.logstreams.impl.LoggedEventImpl;
import io.zeebe.raft.Raft;
import io.zeebe.raft.protocol.AppendRequest;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.ActorPriority;
import io.zeebe.util.sched.SchedulingHints;

public class FollowerState extends AbstractRaftState {
  public FollowerState(Raft raft, ActorControl raftActor) {
    super(raft, raftActor);
  }

  @Override
  public RaftState getState() {
    return RaftState.FOLLOWER;
  }

  @Override
  protected void onEnterState() {
    super.onEnterState();
    raftActor.setSchedulingHints(SchedulingHints.ioBound((short) 0));
  }

  @Override
  protected void onLeaveState() {
    raftActor.setSchedulingHints(SchedulingHints.cpuBound(ActorPriority.REGULAR));
    super.onLeaveState();
  }

  @Override
  protected void consumeMessage() {
    super.consumeMessage();

    // when there are no more append requests immediately available,
    // flush now and send the ack immediately
    if (!appender.isClosed() && !messageBuffer.hasAvailable()) {
      appender.flushAndAck();
    }
  }

  @Override
  public void appendRequest(final AppendRequest appendRequest) {
    raft.mayStepDown(appendRequest);

    final long previousEventPosition = appendRequest.getPreviousEventPosition();
    final int previousEventTerm = appendRequest.getPreviousEventTerm();
    final LoggedEventImpl event = appendRequest.getEvent();

    if (!appender.isClosed() && raft.isTermCurrent(appendRequest)) {
      final boolean lastEvent = appender.isLastEvent(previousEventPosition, previousEventTerm);
      if (lastEvent) {
        appender.appendEvent(appendRequest, event);
      } else {
        appender.truncateLog(appendRequest, event);
      }
    } else {
      rejectAppendRequest(appendRequest, appender.getLastPosition());
    }
  }
}
