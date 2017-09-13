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
import io.zeebe.transport.RemoteAddress;

public class RaftMember
{
    private final RemoteAddress remoteAddress;
    private final LogStream logStream;
    private final BufferedLogStreamReader reader;

    private long heartbeat;
    private boolean failures;

    private long matchPosition;

    private LoggedEventImpl bufferedEvent;
    private long previousPosition;
    private int previousTerm;

    public RaftMember(final RemoteAddress remoteAddress, final LogStream logStream)
    {
        this.remoteAddress = remoteAddress;
        this.logStream = logStream;
        this.reader = new BufferedLogStreamReader(logStream, true);
    }

    public void close()
    {
        reader.close();
    }

    public RemoteAddress getRemoteAddress()
    {
        return remoteAddress;
    }

    public long getMatchPosition()
    {
        return matchPosition;
    }

    public void setMatchPosition(long matchPosition)
    {
        this.matchPosition = matchPosition;
    }

    public long getPreviousPosition()
    {
        return previousPosition;
    }

    public int getPreviousTerm()
    {
        return previousTerm;
    }

    public long getHeartbeat()
    {
        return heartbeat;
    }

    public void setHeartbeat(final long heartbeat)
    {
        this.heartbeat = heartbeat;
    }

    public void failure()
    {
        failures = true;
    }

    public void resetFailures()
    {
        failures = false;
    }

    public boolean hasFailures()
    {
        return failures;
    }

    public void setBufferedEvent(final LoggedEventImpl bufferedEvent)
    {
        this.bufferedEvent = bufferedEvent;
    }

    public LoggedEventImpl discardBufferedEvent()
    {
        final LoggedEventImpl event = bufferedEvent;
        bufferedEvent = null;
        return event;
    }

    public void reset(final long nextHeartbeat)
    {
        setHeartbeat(nextHeartbeat);
        resetFailures();
        setPreviousEventToEndOfLog();
    }

    public LoggedEventImpl getNextEvent()
    {
        if (bufferedEvent != null)
        {
            return discardBufferedEvent();
        }
        else if (reader.hasNext())
        {
            return (LoggedEventImpl) reader.next();
        }
        else
        {
            return null;
        }
    }

    public void resetToPosition(final long eventPosition)
    {
        if (eventPosition >= 0)
        {
            final LoggedEvent previousEvent = getEventAtPosition(eventPosition);
            if (previousEvent != null)
            {
                setPreviousEvent(previousEvent);
            }
            else
            {
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

                long previousPosition = -1;

                while (reader.hasNext())
                {
                    final LoggedEvent next = reader.next();

                    if (next.getPosition() < eventPosition)
                    {
                        previousPosition = next.getPosition();
                    }
                    else
                    {
                        break;
                    }
                }

                if (previousPosition >= 0)
                {
                    setPreviousEvent(previousPosition);
                }
                else
                {
                    setPreviousEventToStartOfLog();
                }
            }
        }
        else
        {
            setPreviousEventToStartOfLog();
        }
    }

    private LoggedEvent getEventAtPosition(final long position)
    {
        if (reader.seek(position) && reader.hasNext())
        {
            return reader.next();
        }
        else
        {
            return null;
        }
    }

    public void setPreviousEventToEndOfLog()
    {
        discardBufferedEvent();

        reader.seekToLastEvent();

        final LoggedEventImpl lastEvent = getNextEvent();
        setPreviousEvent(lastEvent);
    }

    public void setPreviousEventToStartOfLog()
    {
        discardBufferedEvent();

        reader.seekToFirstEvent();

        setPreviousEvent(null);
    }

    public void setPreviousEvent(final long previousPosition)
    {
        discardBufferedEvent();

        final LoggedEvent previousEvent = getEventAtPosition(previousPosition);

        setPreviousEvent(previousEvent);
    }

    public void setPreviousEvent(final LoggedEvent previousEvent)
    {
        discardBufferedEvent();

        if (previousEvent != null)
        {
            previousPosition = previousEvent.getPosition();
            previousTerm = previousEvent.getRaftTerm();
        }
        else
        {
            previousPosition = previousEventPositionNullValue();
            previousTerm = previousEventTermNullValue();
        }
    }

}
