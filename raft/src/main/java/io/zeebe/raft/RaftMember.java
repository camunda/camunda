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

import static io.zeebe.raft.AppendRequestEncoder.previousEventPositionNullValue;
import static io.zeebe.raft.AppendRequestEncoder.previousEventTermNullValue;

import io.zeebe.logstreams.impl.LoggedEventImpl;
import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.transport.RemoteAddress;
import org.slf4j.Logger;

public class RaftMember
{
    private final BrokerEventMetadata metadata = new BrokerEventMetadata();

    private final RemoteAddress remoteAddress;
    private final LogStream logStream;
    private final BufferedLogStreamReader reader;
    private final Logger logger;

    private long heartbeat;
    private boolean failures;

    private long matchPosition;

    private LoggedEventImpl bufferedEvent;
    private long previousPosition;
    private int previousTerm;

    public RaftMember(final RemoteAddress remoteAddress, final LogStream logStream, final Logger logger)
    {
        this.remoteAddress = remoteAddress;
        this.logStream = logStream;
        this.reader = new BufferedLogStreamReader(logStream, true);
        this.logger = logger;
    }

    public RemoteAddress getRemoteAddress()
    {
        return remoteAddress;
    }

    public long getMatchPosition()
    {
        return matchPosition;
    }

    public long getPreviousPosition()
    {
        return previousPosition;
    }

    public int getPreviousTerm()
    {
        return previousTerm;
    }

    public void setHeartbeat(final long heartbeat)
    {
        this.heartbeat = heartbeat;
    }

    public long getHeartbeat()
    {
        return heartbeat;
    }

    public void resetFailures()
    {
        failures = false;
    }

    public boolean hasFailures()
    {
        return failures;
    }

    public void resetPreviousEvent()
    {
        final LoggedEventImpl event = seekToLastEvent();

        if (event != null)
        {
            event.readMetadata(metadata);

            previousPosition = event.getPosition();
            previousTerm = metadata.getRaftTermId();
        }
        else
        {
            previousPosition = previousEventPositionNullValue();
            previousTerm = previousEventTermNullValue();
        }

    }

    private LoggedEventImpl seekToLastEvent()
    {
        bufferedEvent = null;
        reader.seekToLastEvent();
        return getNextEvent();
    }

    public LoggedEventImpl getNextEvent()
    {
        if (bufferedEvent == null && reader.hasNext())
        {
            bufferedEvent = (LoggedEventImpl) reader.next();
        }

        return bufferedEvent;
    }

    public void setMatchPosition(final long matchPosition)
    {
        this.matchPosition = matchPosition;
    }

    public void failure()
    {
        failures = true;
    }

    public void reset(final long nextHeartbeat)
    {
        setHeartbeat(nextHeartbeat);
        resetFailures();
        resetPreviousEvent();
    }

    public void resetToPreviousPosition(final long eventPosition)
    {
        bufferedEvent = null;

        final LogBlockIndex blockIndex = logStream.getLogBlockIndex();
        long blockPosition = blockIndex.lookupBlockPosition(eventPosition);

        if (blockPosition == eventPosition)
        {
            blockPosition = blockIndex.lookupBlockPosition(eventPosition - 1);
        }

        if (blockPosition > 0)
        {
            reader.seek(blockPosition);
        }
        else
        {
            reader.seekToFirstEvent();
        }

        long previousPosition = blockPosition;

        while (reader.hasNext())
        {
            final LoggedEvent next = reader.next();
            if (next.getPosition() < eventPosition)
            {
                next.readMetadata(metadata);
                previousPosition = next.getPosition();
            }
            else
            {
                break;
            }
        }

        if (previousPosition >= 0)
        {
            reader.seek(previousPosition);
            final LoggedEvent previousEvent = getNextEvent();

            if (previousEvent != null)
            {
                setPreviousEvent(previousEvent);
            }
            else
            {
                logger.error("Unable to find event for previous position {}", previousPosition);
            }
        }
        else
        {
            reader.seekToFirstEvent();
            setPreviousEvent(null);
        }
    }


    public void resetToPosition(final long eventPosition)
    {
        bufferedEvent = null;

        if (eventPosition >= 0)
        {
            if (reader.seek(eventPosition) && reader.hasNext())
            {
                final LoggedEvent event = reader.next();

                if (event != null)
                {
                    setPreviousEvent(event);
                }
            }
            else
            {
                setPreviousEvent(null);

                final LogBlockIndex logBlockIndex = logStream.getLogBlockIndex();
                final long blockPosition = logBlockIndex.lookupBlockPosition(eventPosition);

                if (blockPosition > 0)
                {
                    reader.seek(blockPosition);
                }
                else
                {
                    reader.seekToFirstEvent();
                }

                while (reader.hasNext())
                {
                    final LoggedEvent next = reader.next();

                    if (next.getPosition() < eventPosition)
                    {
                        setPreviousEvent(next);
                    }
                    else
                    {
                        break;
                    }
                }
            }
        }
        else
        {
            reader.seekToFirstEvent();
            setPreviousEvent(null);
        }
    }

    public void setPreviousEvent(final LoggedEvent event)
    {
        bufferedEvent = null;

        if (event != null)
        {
            event.readMetadata(metadata);

            previousPosition = event.getPosition();
            previousTerm = metadata.getRaftTermId();
        }
        else
        {
            previousPosition = previousEventPositionNullValue();
            previousTerm = previousEventTermNullValue();
        }
    }

}
