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

import io.zeebe.raft.Raft;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public interface ConsensusRequestHandler
{

    /**
     * The name of the request type, i.e. vote or poll.
     */
    String requestName();

    /**
     * Creates specific request.
     */
    BufferWriter createRequest(Raft raft, long lastEventPosition, int lastTerm);

    /**
     * Checks if the response was granted, quorum was reached.
     */
    boolean isResponseGranted(Raft raft, DirectBuffer responseBuffer);

    /**
     * Callback if a consensus was reached.
     */
    void consensusGranted(Raft raft);

    /**
     * Callback if a consensus was not reached.
     */
    void consensusFailed(Raft raft);

    /**
     * Reset the handler.
     */
    void reset();

}
