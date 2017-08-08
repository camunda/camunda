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
package io.zeebe.client.event.impl;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.zeebe.client.event.RaftEvent;
import io.zeebe.client.event.TopicEventType;
import io.zeebe.transport.SocketAddress;

public class RaftEventImpl extends EventImpl implements RaftEvent
{

    @JsonCreator
    public RaftEventImpl()
    {
        super(TopicEventType.RAFT, null); // raft events have currently no state
    }

    protected List<SocketAddress> members;

    @Override
    public List<SocketAddress> getMembers()
    {
        return members;
    }

    public void setMembers(final List<SocketAddress> members)
    {
        this.members = members;
    }

    @Override
    public String toString()
    {
        return "RaftEventImpl{" +
            "members=" + members +
            '}';
    }
}
