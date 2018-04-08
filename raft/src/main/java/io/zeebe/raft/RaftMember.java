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
package io.zeebe.raft;

import io.zeebe.raft.controller.MemberReplicateLogController;
import io.zeebe.transport.RemoteAddress;

public class RaftMember
{
    private final RemoteAddress remoteAddress;
    private MemberReplicateLogController replicationController;

    private long matchPosition;

    public RaftMember(final RemoteAddress remoteAddress)
    {
        this.remoteAddress = remoteAddress;
    }

    public RemoteAddress getRemoteAddress()
    {
        return remoteAddress;
    }

    public void onFollowerHasAcknowledgedPosition(long position)
    {
        matchPosition = position;
        replicationController.onFollowerHasAcknowledgedPosition(position);
    }

    public void onFollowerHasFailedPosition(long position)
    {
        replicationController.onFollowerHasFailedPosition(position);
    }

    public long getMatchPosition()
    {
        return matchPosition;
    }

    public void setReplicationController(MemberReplicateLogController replicationController)
    {
        this.replicationController = replicationController;
    }
}
