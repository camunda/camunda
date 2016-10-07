package org.camunda.tngp.broker.clustering.raft.protocol;

import org.camunda.tngp.broker.clustering.raft.message.VoteRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteResponse;
import org.camunda.tngp.broker.clustering.raft.protocol.controller.AppendController;
import org.camunda.tngp.broker.clustering.raft.protocol.controller.VoteController;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.logstreams.impl.log.index.LogBlockIndex;
import org.camunda.tngp.logstreams.log.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.log.LogStreamReader;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.log.StreamContext;

/**
 * A member is a reference to another node involved in the raft protocol.
 */
public class Member
{
    protected final Raft raft;
    protected final Endpoint endpoint;

    protected final LogStreamReader logStreamReader;
    protected final BrokerEventMetadata metadata = new BrokerEventMetadata();

    protected final AppendController appendController;
    protected final VoteController voteController;

    protected long currentEntryPosition = -1L;
    protected int currentEntryTerm = -1;
    protected int failures = 0;
    protected long matchPosition = -1L;
    protected long lastActivity = -1L;

    protected Type type;

    protected int index;

    public Member(final Endpoint endpoint, final Type type)
    {
        this.endpoint = endpoint;
        this.type = type;

        this.raft = null;
        this.logStreamReader = null;
        this.appendController = null;
        this.voteController = null;
    }

    public Member(final Raft raft, final Endpoint endpoint, final Type type)
    {
        this.raft = raft;
        this.logStreamReader = new BufferedLogStreamReader(raft.stream());
        this.endpoint = endpoint;
        this.type = type;
        this.appendController = new AppendController(raft, this);
        this.voteController = new VoteController(raft, this);
    }

    public Endpoint endpoint()
    {
        return endpoint;
    }

    public boolean hasNextEntry()
    {
        return logStreamReader.hasNext();
    }

    public LoggedEvent nextEntry()
    {
        final LoggedEvent nextEntry = logStreamReader.next();
        nextEntry.readMetadata(metadata);

        currentEntryPosition = nextEntry.getPosition();
        currentEntryTerm = metadata.getRaftTermId();

        return nextEntry;
    }

    public boolean hasFailures()
    {
        return failures > 0;
    }

    public void incrementFailures()
    {
        failures++;
    }

    public int index()
    {
        return index;
    }

    public void incrementIndex()
    {
        index++;
    }

    public Member failures(int failures)
    {
        if (failures < 0)
        {
            throw new IllegalArgumentException();
        }
        this.failures = failures;
        return this;
    }

    public Type type()
    {
        return type;
    }

    public Member type(final Type type)
    {
        this.type = type;
        return this;
    }

    public long lastActivity()
    {
        return lastActivity;
    }

    public Member lastActivity(final long lastActivity)
    {
        this.lastActivity = lastActivity;
        return this;
    }

    public long currentEntryPosition()
    {
        return currentEntryPosition;
    }

    public Member currentEntryPosition(final long currentEntryPosition)
    {
        this.currentEntryPosition = currentEntryPosition;
        return this;
    }

    public int currentEntryTerm()
    {
        return currentEntryTerm;
    }

    public Member currentEntryTerm(final int currentEntryTerm)
    {
        this.currentEntryTerm = currentEntryTerm;
        return this;
    }

    public void resetToLastEntry()
    {
        currentEntryPosition = -1;
        currentEntryTerm = -1;

        logStreamReader.seekToLastEvent();

        if (logStreamReader.hasNext())
        {
            final LoggedEvent lastEntry = logStreamReader.next();
            metadata.wrap(lastEntry.getMetadata(), lastEntry.getMetadataOffset(), lastEntry.getMetadataLength());

            currentEntryPosition = lastEntry.getPosition();
            currentEntryTerm = metadata.getRaftTermId();
        }
    }

    public void resetToPreviousEntry(long position)
    {
        if (position < currentEntryPosition)
        {
            currentEntryPosition = -1;
            currentEntryTerm = -1;

            if (position < 0)
            {
                logStreamReader.seekToFirstEvent();
                return;
            }

            final Raft raft = this.raft;
            final LogStream stream = raft.stream();
            final StreamContext context = stream.getContext();
            final LogBlockIndex blockIndex = context.getBlockIndex();

            long blockPosition = blockIndex.lookupBlockPosition(position);
            if (blockPosition == position)
            {
                blockPosition = blockIndex.lookupBlockPosition(position - 1);
            }

            if (blockPosition >= 0)
            {
                logStreamReader.seek(blockPosition);
            }
            else
            {
                logStreamReader.seekToFirstEvent();
            }


            while (logStreamReader.hasNext())
            {
                final LoggedEvent curr = logStreamReader.next();
                metadata.wrap(curr.getMetadata(), curr.getMetadataOffset(), curr.getMetadataLength());

                if (curr.getPosition() < position)
                {
                    currentEntryPosition = curr.getPosition();
                    currentEntryTerm = metadata.getRaftTermId();
                }
                else
                {
                    break;
                }
            }

            if (currentEntryPosition >= 0)
            {
                logStreamReader.seek(this.currentEntryPosition);
                logStreamReader.next();
            }
            else
            {
                logStreamReader.seekToFirstEvent();
            }
        }
    }

    public int doVote()
    {
        int workcount = 0;
        workcount += voteController.doWork();
        return workcount;
    }

    public void sendVoteRequest(final VoteRequest voteRequest)
    {
        voteController.open(voteRequest);
    }

    public void cancelVoteRequest()
    {
        if (!voteController.isClosed())
        {
            voteController.close();
            voteController.doWork();
        }
    }

    public VoteResponse getVoteResponse()
    {
        return voteController.getVoteResponse();
    }

    public int doAppend()
    {
        int workcount = 0;
        workcount += appendController.doWork();
        return workcount;
    }

    public void sendAppendRequest()
    {
        appendController.open();
    }

    public void cancelAppendRequest()
    {
        if (!appendController.isClosed())
        {
            appendController.close();
            appendController.doWork();
        }
    }

    public boolean shouldAppend()
    {
        return shouldAppendNextEntry() || shouldSendHeartbeat();
    }

    protected boolean shouldAppendNextEntry()
    {
        return !hasFailures() && appendController.isLastAppendEntriesSended() && hasNextEntry();
    }

    protected boolean shouldSendHeartbeat()
    {
        return System.currentTimeMillis() >= lastActivity + 100;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((endpoint == null) ? 0 : endpoint.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (getClass() != obj.getClass())
        {
            return false;
        }
        final Member other = (Member) obj;
        if (endpoint == null)
        {
            if (other.endpoint != null)
            {
                return false;
            }
        }
        else if (!endpoint.equals(other.endpoint))
        {
            return false;
        }
        return true;
    }

    public enum Type
    {
        INACTIVE,
        ACTIVE
    }

}
