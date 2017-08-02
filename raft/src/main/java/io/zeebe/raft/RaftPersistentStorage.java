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

import io.zeebe.transport.SocketAddress;

/**
 * Raft requires some information to be stored in persistent storage and be available
 * throughout restarts of the raft node. Additionally to the required term and voteFor
 * this also keeps a list of currently known members.
 */
public interface RaftPersistentStorage
{

    /**
     * @return the current term
     */
    int getTerm();

    /**
     * Set the the current term.
     *
     * @param term the current term, which should be a positive integer
     */
    RaftPersistentStorage setTerm(int term);

    /**
     * @return the member which was granted the vote for the current term
     */
    SocketAddress getVotedFor();

    /**
     * Set the member which was granted the vote for the current term.
     *
     * @param votedFor the address of the member the vote was granted or null to clear the voteFor property
     */
    RaftPersistentStorage setVotedFor(SocketAddress votedFor);

    /**
     * Add a new member to the members list.
     */
    RaftPersistentStorage addMember(SocketAddress member);

    /**
     * Clears the list of members.
     */
    RaftPersistentStorage clearMembers();

    /**
     * Blocks until the current state is persisted.
     */
    RaftPersistentStorage save();

}
