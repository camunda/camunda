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

import static io.zeebe.raft.PollRequestEncoder.lastEventPositionNullValue;
import static io.zeebe.raft.PollRequestEncoder.lastEventTermNullValue;

import java.util.ArrayList;
import java.util.List;

import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftMember;
import io.zeebe.raft.protocol.PollRequest;
import io.zeebe.raft.protocol.PollResponse;
import io.zeebe.transport.ClientRequest;
import io.zeebe.util.state.*;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class PollController
{

    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_OPEN = 1;
    private static final int TRANSITION_CLOSE = 2;

    private static final StateMachineCommand<Context> OPEN_COMMAND = context -> context.take(TRANSITION_OPEN);
    private static final StateMachineCommand<Context> CLOSE_COMMAND = context -> context.take(TRANSITION_CLOSE);

    private final StateMachine<Context> stateMachine;
    private final StateMachineAgent<Context> stateMachineAgent;

    public PollController(final Raft raft)
    {
        final State<Context> opening = new OpeningState();
        final State<Context> open = new OpenState();
        final State<Context> closing = new ClosingState();
        final WaitState<Context> closed = context -> { };

        stateMachine = StateMachine.<Context>builder(s -> new Context(s, raft))
            .initialState(closed)
            .from(closed).take(TRANSITION_OPEN).to(opening)
            .from(closed).take(TRANSITION_CLOSE).to(closed)
            .from(opening).take(TRANSITION_DEFAULT).to(open)
            .from(open).take(TRANSITION_OPEN).to(open)
            .from(open).take(TRANSITION_CLOSE).to(closing)
            .from(closing).take(TRANSITION_DEFAULT).to(closed)
            .build();

        stateMachineAgent = new LimitedStateMachineAgent<>(stateMachine);
    }

    public int doWork()
    {
        return stateMachineAgent.doWork();
    }

    public void reset()
    {
        stateMachineAgent.reset();
        stateMachine.getContext().close();
    }

    public void open()
    {
        stateMachineAgent.addCommand(OPEN_COMMAND);
    }

    public void close()
    {
        stateMachineAgent.addCommand(CLOSE_COMMAND);
    }

    static class OpeningState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            final PollRequest pollRequest = createPollRequest(context);

            final int workCount = sendPollRequestToMembers(context, pollRequest);

            context.take(TRANSITION_DEFAULT);

            return workCount;
        }

        protected PollRequest createPollRequest(final Context context)
        {
            final LoggedEvent lastEvent = context.getLastEvent();

            final long lastEventPosition;
            final int lastEventTerm;

            if (lastEvent != null)
            {
                final BrokerEventMetadata metadata = context.getMetadata();
                lastEvent.readMetadata(metadata);

                lastEventPosition = lastEvent.getPosition();
                lastEventTerm = metadata.getRaftTermId();
            }
            else
            {
                lastEventPosition = lastEventPositionNullValue();
                lastEventTerm = lastEventTermNullValue();
            }

            return context.getPollRequest()
                           .reset()
                           .setRaft(context.getRaft())
                           .setLastEventPosition(lastEventPosition)
                           .setLastEventTerm(lastEventTerm);
        }

        protected int sendPollRequestToMembers(final Context context, final PollRequest pollRequest)
        {
            final Raft raft = context.getRaft();
            final Logger logger = raft.getLogger();

            final int memberSize = raft.getMemberSize();
            for (int i = 0; i < memberSize; i++)
            {
                final RaftMember member = raft.getMember(i);

                try
                {
                    final ClientRequest clientRequest = raft.sendRequest(member.getRemoteAddress(), pollRequest);

                    if (clientRequest != null)
                    {
                        context.addClientRequest(clientRequest);
                    }
                }
                catch (final Exception e)
                {
                    logger.debug("Failed to send poll request to {}", member.getRemoteAddress(), e);
                }

            }

            logger.debug("Poll request send to {} other members", memberSize);

            return memberSize;
        }

        @Override
        public boolean isInterruptable()
        {
            return false;
        }
    }

    static class OpenState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            final Raft raft = context.getRaft();
            final Logger logger = raft.getLogger();

            final int remaingClientRequests = checkPollResponses(context);

            if (context.isGranted())
            {
                logger.debug("Poll request successful with {} votes for a quorum of {}", context.getGranted(), raft.requiredQuorum());
                raft.becomeCandidate();
                context.take(TRANSITION_CLOSE);
            }
            else if (remaingClientRequests == 0)
            {
                logger.debug("Poll request failed with {} votes for a quorum of {}", context.getGranted(), raft.requiredQuorum());
                context.take(TRANSITION_CLOSE);
            }

            return 1;
        }

        protected int checkPollResponses(final Context context)
        {
            final Raft raft = context.getRaft();
            final Logger logger = raft.getLogger();
            final PollResponse pollResponse = context.getPollResponse();

            final List<ClientRequest> clientRequests = context.getClientRequests();

            for (int i = 0; i < clientRequests.size(); i++)
            {
                final ClientRequest clientRequest = clientRequests.get(i);

                if (clientRequest.isDone())
                {
                    try
                    {

                        final DirectBuffer responseBuffer = clientRequest.get();
                        pollResponse.wrap(responseBuffer, 0, responseBuffer.capacity());

                        // only register response from the current term
                        if (!raft.mayStepDown(pollResponse) && raft.isTermCurrent(pollResponse))
                        {
                            if (pollResponse.isGranted())
                            {
                                context.registerGranted();
                            }
                        }

                    }
                    catch (final Exception e)
                    {
                        logger.debug("Failed to receive poll response", e);
                    }
                    finally
                    {
                        clientRequest.close();
                        clientRequests.remove(i);
                        i--;
                    }
                }
            }

            return clientRequests.size();
        }

    }

    static class ClosingState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            // abort remaining requests and reset context for next iteration
            context.reset();

            context.take(TRANSITION_DEFAULT);

            return 1;
        }

        @Override
        public boolean isInterruptable()
        {
            return false;
        }

    }

    static class Context extends SimpleStateMachineContext
    {

        private final Raft raft;
        private final BufferedLogStreamReader reader;

        private final List<ClientRequest> clientRequests = new ArrayList<>();

        private final BrokerEventMetadata metadata = new BrokerEventMetadata();
        private final PollRequest pollRequest = new PollRequest();
        private final PollResponse pollResponse = new PollResponse();

        private int granted;

        Context(final StateMachine<Context> stateMachine, final Raft raft)
        {
            super(stateMachine);

            this.raft = raft;
            this.reader = new BufferedLogStreamReader(raft.getLogStream(), true);

            reset();
        }

        @Override
        public void reset()
        {
            for (final ClientRequest clientRequest : clientRequests)
            {
                clientRequest.close();
            }
            clientRequests.clear();

            pollRequest.reset();
            pollResponse.reset();
            granted = 1; // always vote for self
        }

        public void close()
        {
            reset();
            reader.close();
        }

        public List<ClientRequest> getClientRequests()
        {
            return clientRequests;
        }

        public void addClientRequest(final ClientRequest request)
        {
            this.clientRequests.add(request);
        }

        public Raft getRaft()
        {
            return raft;
        }

        public LoggedEvent getLastEvent()
        {
            reader.seekToLastEvent();

            if (reader.hasNext())
            {
                return reader.next();
            }
            else
            {
                return null;
            }
        }

        public BrokerEventMetadata getMetadata()
        {
            return metadata;
        }

        public PollRequest getPollRequest()
        {
            return pollRequest;
        }

        public void registerGranted()
        {
            granted++;
        }

        public boolean isGranted()
        {
            return granted >= raft.requiredQuorum();
        }

        public PollResponse getPollResponse()
        {
            return pollResponse;
        }

        public int getGranted()
        {
            return granted;
        }
    }

}
